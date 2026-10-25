package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("semgrep-integration")
@EnabledIfSystemProperty(named = "runSemgrepIntegration", matches = "true")
class SemgrepAnalysisServiceCliIntegrationTest {

    private static final Duration CLI_CHECK_TIMEOUT = Duration.ofSeconds(10);

    private Path temporaryRoot;

    @AfterEach
    void cleanUpSourceFiles() throws IOException {
        if (temporaryRoot != null) {
            deleteTree(temporaryRoot);
            assertFalse(Files.exists(temporaryRoot), "Temporary Semgrep source files must be removed.");
        }
    }

    @Test
    void scansLocalJavaAndPythonRulesAndCleansAllTemporaryFiles() throws IOException {
        String executable = configuredExecutable();
        assumeSemgrepAvailable(executable);

        temporaryRoot = Files.createTempDirectory("semgrep-cli-integration-");
        Path sourceDirectory = Files.createDirectories(temporaryRoot.resolve("source"));
        Path javaFile = sourceDirectory.resolve("src/Example.java");
        Path pythonFile = sourceDirectory.resolve("scripts/example.py");
        Files.createDirectories(javaFile.getParent());
        Files.createDirectories(pythonFile.getParent());
        Files.writeString(javaFile, """
                class Example {
                    void run() {
                        System.out.println("SEMGREP_JAVA_MARKER");
                    }
                }
                """);
        Files.writeString(pythonFile, """
                def run():
                    print("SEMGREP_PYTHON_MARKER")
                """);

        Path ruleFile = temporaryRoot.resolve("local-rules.yaml");
        Files.writeString(ruleFile, """
                rules:
                  - id: integration.java-marker
                    languages: [java]
                    message: Java marker was detected
                    severity: ERROR
                    pattern: 'System.out.println("SEMGREP_JAVA_MARKER");'
                  - id: integration.python-marker
                    languages: [python]
                    message: Python marker was detected
                    severity: WARNING
                    pattern: 'print("SEMGREP_PYTHON_MARKER")'
                """);

        SemgrepAnalysisService service = new SemgrepAnalysisService(
                new ObjectMapper(), executable, ruleFile.toString(), 60);
        Set<Path> temporaryOutputsBeforeScan = semgrepTemporaryOutputs();

        List<ReviewFinding> findings = service.analyze(sourceDirectory);

        assertSemgrepTemporaryOutputsCleaned(temporaryOutputsBeforeScan);
        assertEquals(2, findings.size());

        ReviewFinding javaFinding = findings.stream()
                .filter(finding -> hasRuleId(finding, "integration.java-marker"))
                .findFirst()
                .orElseThrow();
        assertEquals("src/Example.java", javaFinding.getFile());
        assertEquals(3, javaFinding.getLine());
        assertEquals("High", javaFinding.getSeverity());
        assertEquals("Java marker was detected", javaFinding.getProblem());

        ReviewFinding pythonFinding = findings.stream()
                .filter(finding -> hasRuleId(finding, "integration.python-marker"))
                .findFirst()
                .orElseThrow();
        assertEquals("scripts/example.py", pythonFinding.getFile());
        assertEquals(2, pythonFinding.getLine());
        assertEquals("Medium", pythonFinding.getSeverity());
        assertEquals("Python marker was detected", pythonFinding.getProblem());

        Path cleanSourceDirectory = Files.createDirectories(temporaryRoot.resolve("clean-source"));
        Files.writeString(cleanSourceDirectory.resolve("Clean.java"), """
                class Clean {
                    void run() {
                        System.out.println("NO_MATCH");
                    }
                }
                """);
        Set<Path> beforeEmptyScan = semgrepTemporaryOutputs();
        assertEquals(List.of(), service.analyze(cleanSourceDirectory));
        assertSemgrepTemporaryOutputsCleaned(beforeEmptyScan);

        Path invalidRules = temporaryRoot.resolve("invalid-rules.yaml");
        Files.writeString(invalidRules, "rules: [this is not a valid rule configuration");
        SemgrepAnalysisService invalidConfigurationService = new SemgrepAnalysisService(
                new ObjectMapper(), executable, invalidRules.toString(), 60);
        Set<Path> beforeFailedScan = semgrepTemporaryOutputs();
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> invalidConfigurationService.analyze(sourceDirectory));
        assertTrue(failure.getMessage().startsWith("Semgrep analysis failed with exit code "));
        assertSemgrepTemporaryOutputsCleaned(beforeFailedScan);
    }

    private static boolean hasRuleId(ReviewFinding finding, String ruleId) {
        String category = finding.getCategory();
        return category != null && (category.equals(ruleId)
                || category.endsWith("." + ruleId)
                || category.endsWith(":" + ruleId)
                || category.endsWith("/" + ruleId)
                || category.endsWith("\\" + ruleId));
    }

    private static String configuredExecutable() {
        String configured = System.getenv("SEMGREP_EXECUTABLE");
        return configured == null || configured.isBlank() ? "semgrep" : configured;
    }

    private static void assumeSemgrepAvailable(String executable) {
        Process process;
        try {
            process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException | SecurityException e) {
            Assumptions.assumeTrue(false,
                    "Semgrep CLI integration test skipped: executable '" + executable
                            + "' is unavailable; set SEMGREP_EXECUTABLE to the installed CLI.");
            return;
        }

        try {
            boolean completed = process.waitFor(CLI_CHECK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            Assumptions.assumeTrue(completed && process.exitValue() == 0,
                    "Semgrep CLI integration test skipped: executable '" + executable
                            + "' did not complete '--version' successfully.");
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            Assumptions.assumeTrue(false,
                    "Semgrep CLI integration test skipped because checking the executable was interrupted.");
        }
    }

    private static Set<Path> semgrepTemporaryOutputs() throws IOException {
        Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(SemgrepAnalysisServiceCliIntegrationTest::isSemgrepTemporaryOutput)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        }
    }

    private static boolean isSemgrepTemporaryOutput(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith("semgrep-")
                && (name.endsWith(".json") || name.endsWith(".log"));
    }

    private static void assertSemgrepTemporaryOutputsCleaned(Set<Path> beforeScan) throws IOException {
        assertEquals(beforeScan, semgrepTemporaryOutputs(),
                "Semgrep JSON and diagnostic temporary files must be deleted after each scan.");
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted((first, second) ->
                    Integer.compare(second.getNameCount(), first.getNameCount())).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
