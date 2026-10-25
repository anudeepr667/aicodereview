package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("semgrep-integration")
@EnabledIfSystemProperty(named = "runSemgrepIntegration", matches = "true")
class SemgrepLanguageCoverageCliIntegrationTest {

    private static final long SCAN_TIMEOUT_SECONDS = 60;
    private static final Duration CLI_CHECK_TIMEOUT = Duration.ofSeconds(10);
    private static final String MARKER = "SEMGREP_LANGUAGE_COVERAGE_MARKER";

    private Path temporaryRoot;

    static Stream<LanguageCase> languageCases() {
        return Stream.of(
                new LanguageCase("Java", "java", "java", "Example.java",
                        "class Example {\n    void run() { System.out.println(\"" + MARKER + "\"); }\n}\n",
                        "System.out.println(\"" + MARKER + "\");", 2),
                new LanguageCase("Python", "python", "py", "example.py",
                        "def run():\n    print(\"" + MARKER + "\")\n",
                        "print(\"" + MARKER + "\")", 2),
                new LanguageCase("JavaScript", "javascript", "js", "example.js",
                        "function run() {\n    console.log(\"" + MARKER + "\");\n}\n",
                        "console.log(\"" + MARKER + "\");", 2),
                new LanguageCase("TypeScript", "typescript", "ts", "example.ts",
                        "function run(): void {\n    console.log(\"" + MARKER + "\");\n}\n",
                        "console.log(\"" + MARKER + "\");", 2),
                new LanguageCase("Go", "go", "go", "example.go",
                        "package main\nimport \"fmt\"\nfunc main() {\n    fmt.Println(\"" + MARKER + "\")\n}\n",
                        "fmt.Println(\"" + MARKER + "\")", 4),
                new LanguageCase("Kotlin", "kotlin", "kt", "Example.kt",
                        "fun main() {\n    println(\"" + MARKER + "\")\n}\n",
                        "println(\"" + MARKER + "\")", 2),
                new LanguageCase("Ruby", "ruby", "rb", "example.rb",
                        "def run\n  puts \"" + MARKER + "\"\nend\n",
                        "puts \"" + MARKER + "\"", 2),
                new LanguageCase("PHP", "php", "php", "example.php",
                        "<?php\nfunction run() {\n    echo \"" + MARKER + "\";\n}\n",
                        "echo \"" + MARKER + "\";", 3),
                new LanguageCase("C", "c", "c", "example.c",
                        "#include <stdio.h>\nint main(void) {\n    printf(\"" + MARKER + "\");\n    return 0;\n}\n",
                        "printf(\"" + MARKER + "\");", 3),
                new LanguageCase("C++", "cpp", "cpp", "example.cpp",
                        "#include <iostream>\nint main() {\n    std::cout << \"" + MARKER + "\";\n    return 0;\n}\n",
                        "std::cout << \"" + MARKER + "\";", 3),
                new LanguageCase("C#", "csharp", "cs", "Example.cs",
                        "using System;\nclass Example {\n    static void Main() { Console.WriteLine(\"" + MARKER + "\"); }\n}\n",
                        "Console.WriteLine(\"" + MARKER + "\");", 3));
    }

    @AfterEach
    void cleanUpTemporarySourceAndRuleFiles() throws IOException {
        if (temporaryRoot != null) {
            deleteTree(temporaryRoot);
            assertFalse(Files.exists(temporaryRoot),
                    "Temporary language source and rule files must be removed.");
        }
    }

    @ParameterizedTest(name = "{0} Semgrep parser and finding mapping")
    @MethodSource("languageCases")
    void parsesScansAndMapsFindingForEachLanguage(LanguageCase language) throws IOException {
        String executable = configuredExecutable();
        assumeSemgrepAvailable(executable);

        temporaryRoot = Files.createTempDirectory("semgrep-language-coverage-");
        Path sourceDirectory = Files.createDirectories(temporaryRoot.resolve("source"));
        Path sourceFile = sourceDirectory.resolve(language.fileName());
        Files.writeString(sourceFile, language.source());

        String ruleId = "coverage." + language.ruleSuffix();
        String message = language.name() + " local marker detected";
        Path ruleFile = temporaryRoot.resolve("local-rule.yaml");
        Files.writeString(ruleFile, """
                rules:
                  - id: %s
                    languages: [%s]
                    message: "%s"
                    severity: ERROR
                    pattern: '%s'
                """.formatted(ruleId, language.semgrepLanguage(), message, language.pattern()));

        SemgrepAnalysisService service = new SemgrepAnalysisService(
                new ObjectMapper(), executable, ruleFile.toString(), SCAN_TIMEOUT_SECONDS);
        Set<Path> outputsBeforeScan = semgrepTemporaryOutputs();
        List<ReviewFinding> findings;
        try {
            findings = service.analyze(sourceDirectory);
        } catch (IllegalStateException scanFailure) {
            assertSemgrepTemporaryOutputsCleaned(outputsBeforeScan);
            String diagnostic = rerunForDiagnostic(executable, ruleFile, sourceDirectory);
            if (isExplicitUnsupportedLanguageDiagnostic(diagnostic)) {
                Assumptions.assumeTrue(false,
                        language.name() + " is UNVERIFIED/UNSUPPORTED by this installed Semgrep engine: "
                                + diagnostic.trim());
            }
            throw new AssertionError(language.name()
                    + " scan failed without an explicit unsupported-language diagnostic: "
                    + scanFailure.getMessage() + "; CLI output: " + diagnostic, scanFailure);
        }
        assertSemgrepTemporaryOutputsCleaned(outputsBeforeScan);

        assertEquals(1, findings.size(),
                language.name() + " scan must return exactly one matching finding.");
        ReviewFinding finding = findings.get(0);
        assertTrue(hasRuleId(finding.getCategory(), ruleId),
                language.name() + " rule ID was not returned: " + finding.getCategory());
        assertEquals(language.fileName(), finding.getFile(),
                "Semgrep finding path must remain repository-relative.");
        assertEquals(language.line(), finding.getLine());
        assertEquals("High", finding.getSeverity());
        assertEquals(message, finding.getProblem());
        System.out.printf("PASS %s: rule=%s file=%s line=%d severity=%s message=%s%n",
                language.name(), finding.getCategory(), finding.getFile(), finding.getLine(),
                finding.getSeverity(), finding.getProblem());
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
                    "Semgrep language coverage skipped: executable '" + executable
                            + "' is unavailable; set SEMGREP_EXECUTABLE to the installed CLI.");
            return;
        }
        try {
            boolean completed = process.waitFor(CLI_CHECK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            Assumptions.assumeTrue(completed && process.exitValue() == 0,
                    "Semgrep language coverage skipped: executable '" + executable
                            + "' did not complete '--version' successfully.");
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            Assumptions.assumeTrue(false,
                    "Semgrep language coverage skipped because checking the executable was interrupted.");
        }
    }

    private String rerunForDiagnostic(
            String executable,
            Path ruleFile,
            Path sourceDirectory) throws IOException {
        Path jsonOutput = temporaryRoot.resolve("diagnostic-output.json");
        Path diagnosticOutput = temporaryRoot.resolve("diagnostic-output.log");
        Process process = new ProcessBuilder(
                executable,
                "scan",
                "--json-output",
                jsonOutput.toString(),
                "--metrics=off",
                "--config",
                ruleFile.toString(),
                sourceDirectory.toString())
                .directory(sourceDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(diagnosticOutput.toFile())
                .start();
        try {
            if (!process.waitFor(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "Diagnostic rerun timed out.";
            }
            return Files.exists(diagnosticOutput) ? Files.readString(diagnosticOutput)
                    : "Diagnostic rerun produced no CLI output (exit " + process.exitValue() + ").";
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return "Diagnostic rerun was interrupted.";
        }
    }

    private static boolean isExplicitUnsupportedLanguageDiagnostic(String diagnostic) {
        String normalized = diagnostic.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("unsupported language")
                || normalized.contains("language is not supported")
                || normalized.contains("no parser") && normalized.contains("language")
                || normalized.contains("unsupported") && normalized.contains("language")
                || normalized.contains("unknown language")
                || normalized.contains("not a valid language")
                || normalized.contains("invalid rule")
                || normalized.contains("invalid pattern");
    }

    private static boolean hasRuleId(String category, String ruleId) {
        return category != null && (category.equals(ruleId)
                || category.endsWith("." + ruleId)
                || category.endsWith(":" + ruleId)
                || category.endsWith("/" + ruleId)
                || category.endsWith("\\" + ruleId));
    }

    private static Set<Path> semgrepTemporaryOutputs() throws IOException {
        Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("semgrep-")
                                && (name.endsWith(".json") || name.endsWith(".log"));
                    })
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        }
    }

    private static void assertSemgrepTemporaryOutputsCleaned(Set<Path> beforeScan) throws IOException {
        assertEquals(beforeScan, semgrepTemporaryOutputs(),
                "Semgrep JSON and diagnostic temporary files must be removed after the scan.");
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted((first, second) ->
                    Integer.compare(second.getNameCount(), first.getNameCount())).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    record LanguageCase(
            String name,
            String semgrepLanguage,
            String ruleSuffix,
            String fileName,
            String source,
            String pattern,
            int line) {
        @Override
        public String toString() {
            return name;
        }
    }
}
