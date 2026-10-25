package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class SemgrepAnalysisService {

    private static final String NO_AUTOMATIC_FIX =
            "No automatic fix provided; review the finding and determine an appropriate correction.";
    private static final long TERMINATION_GRACE_SECONDS = 2;

    private final ObjectMapper objectMapper;
    private final String executable;
    private final String ruleset;
    private final long timeoutSeconds;

    public SemgrepAnalysisService(
            ObjectMapper objectMapper,
            @Value("${SEMGREP_EXECUTABLE:semgrep}") String executable,
            @Value("${semgrep.ruleset:p/default}") String ruleset,
            @Value("${semgrep.timeout-seconds:120}") long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Semgrep timeout must be greater than zero.");
        }
        this.objectMapper = objectMapper;
        this.executable = executable;
        this.ruleset = ruleset;
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<ReviewFinding> analyze(Path sourceDirectory) {
        if (sourceDirectory == null || !Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Source directory must exist and be a directory.");
        }

        Path normalizedSourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        Path jsonOutput = null;
        Path diagnosticOutput = null;
        Process process = null;
        try {
            jsonOutput = Files.createTempFile("semgrep-", ".json");
            Files.delete(jsonOutput);
            diagnosticOutput = Files.createTempFile("semgrep-", ".log");

            ProcessBuilder processBuilder = new ProcessBuilder(
                    executable,
                    "scan",
                    "--json-output",
                    jsonOutput.toString(),
                    "--metrics=off",
                    "--config",
                    ruleset,
                    normalizedSourceDirectory.toString());
            processBuilder.directory(normalizedSourceDirectory.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(diagnosticOutput.toFile());

            try {
                process = processBuilder.start();
            } catch (IOException | SecurityException e) {
                throw new IllegalStateException(
                        "Semgrep executable was not found or could not be started; verify SEMGREP_EXECUTABLE.");
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                if (terminateProcess(process)) {
                    throw new InterruptedException();
                }
                throw new IllegalStateException("Semgrep analysis timed out.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Semgrep analysis failed with exit code " + process.exitValue() + ".");
            }
            if (!Files.isRegularFile(jsonOutput)) {
                throw new IllegalStateException("Semgrep completed without producing JSON output.");
            }

            String json = Files.readString(jsonOutput);
            return parseSemgrepJson(json, normalizedSourceDirectory);
        } catch (InterruptedException e) {
            if (process != null) {
                terminateProcess(process);
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Semgrep analysis was interrupted.");
        } catch (IOException e) {
            throw new IllegalStateException("Semgrep could not create or read its temporary output.");
        } finally {
            deleteTemporaryFiles(jsonOutput, diagnosticOutput);
        }
    }

    List<ReviewFinding> parseSemgrepJson(String json, Path sourceDirectory) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Semgrep produced invalid JSON output.");
        }

        JsonNode results = root == null ? null : root.get("results");
        if (results == null || !results.isArray()) {
            throw new IllegalStateException("Semgrep JSON output does not contain a results array.");
        }

        Path normalizedSourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        List<ReviewFinding> findings = new ArrayList<>();
        for (JsonNode result : results) {
            findings.add(toFinding(result, normalizedSourceDirectory));
        }
        return findings;
    }

    private ReviewFinding toFinding(JsonNode result, Path sourceDirectory) {
        JsonNode extra = result.path("extra");
        ReviewFinding finding = new ReviewFinding();
        finding.setCategory(textValue(result.path("check_id"), "Semgrep"));
        finding.setProblem(textValue(extra.path("message"), "Semgrep reported a finding."));
        finding.setFile(relativeFindingPath(result.path("path"), sourceDirectory));
        finding.setLine(positiveLineNumber(result.path("start").path("line")));
        finding.setSeverity(normalizeSeverity(extra.path("severity")));
        finding.setExplanation(metadataExplanation(extra.path("metadata")));
        finding.setSuggestedFix(textValue(extra.path("fix"), NO_AUTOMATIC_FIX));
        return finding;
    }

    private static String relativeFindingPath(JsonNode pathNode, Path sourceDirectory) {
        if (!pathNode.isTextual() || pathNode.asText().isBlank()) {
            return null;
        }
        try {
            Path findingPath = Path.of(pathNode.asText());
            Path absoluteFindingPath = findingPath.isAbsolute()
                    ? findingPath.normalize()
                    : sourceDirectory.resolve(findingPath).normalize();
            if (!absoluteFindingPath.startsWith(sourceDirectory)) {
                throw new IllegalStateException(
                        "Semgrep returned a finding outside the scanned directory.");
            }
            return sourceDirectory.relativize(absoluteFindingPath)
                    .toString()
                    .replace('\\', '/');
        } catch (InvalidPathException e) {
            throw new IllegalStateException("Semgrep returned an invalid finding path.");
        }
    }

    private static Integer positiveLineNumber(JsonNode lineNode) {
        if (!lineNode.canConvertToInt()) {
            return null;
        }
        int line = lineNode.intValue();
        return line > 0 ? line : null;
    }

    private static String normalizeSeverity(JsonNode severityNode) {
        if (!severityNode.isTextual()) {
            return "Medium";
        }
        return switch (severityNode.asText().trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "Critical";
            case "HIGH", "ERROR" -> "High";
            case "LOW", "INFO" -> "Low";
            case "MEDIUM", "WARNING" -> "Medium";
            default -> "Medium";
        };
    }

    private static String metadataExplanation(JsonNode metadata) {
        for (String key : List.of("description", "message", "impact")) {
            JsonNode value = metadata.path(key);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String textValue(JsonNode node, String fallback) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private static boolean terminateProcess(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
            return false;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static void deleteTemporaryFiles(Path... files) {
        IOException cleanupFailure = null;
        for (Path file : files) {
            if (file == null) {
                continue;
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                if (cleanupFailure == null) {
                    cleanupFailure = e;
                } else {
                    cleanupFailure.addSuppressed(e);
                }
            }
        }
        if (cleanupFailure != null) {
            throw new IllegalStateException("Unable to clean up temporary Semgrep output.");
        }
    }
}
