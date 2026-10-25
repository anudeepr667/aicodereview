package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SemgrepAnalysisServiceTest {

    private SemgrepAnalysisService service;
    private Path sourceDirectory;

    @BeforeEach
    void setUp() {
        service = new SemgrepAnalysisService(new ObjectMapper(), "semgrep", "p/default", 120);
        sourceDirectory = Path.of("semgrep-test-source").toAbsolutePath().normalize();
    }

    @Test
    void mapsFindingFieldsAndAutomaticFix() {
        String json = """
                {
                  "results": [{
                    "check_id": "java.lang.security.audit",
                    "path": "src/App.java",
                    "start": {"line": 12},
                    "extra": {
                      "message": "Potentially unsafe call",
                      "severity": "ERROR",
                      "metadata": {"description": "Use a safer API."},
                      "fix": "safeCall()"
                    }
                  }]
                }
                """;

        ReviewFinding finding = service.parseSemgrepJson(json, sourceDirectory).get(0);

        assertEquals("java.lang.security.audit", finding.getCategory());
        assertEquals("Potentially unsafe call", finding.getProblem());
        assertEquals("src/App.java", finding.getFile());
        assertEquals(12, finding.getLine());
        assertEquals("High", finding.getSeverity());
        assertEquals("Use a safer API.", finding.getExplanation());
        assertEquals("safeCall()", finding.getSuggestedFix());
    }

    @Test
    void usesFallbackWhenFixAndOptionalMetadataAreMissing() {
        String json = """
                {
                  "results": [{
                    "check_id": "python.lang.best-practice",
                    "path": "app.py",
                    "start": {},
                    "extra": {
                      "message": "Review this code",
                      "severity": "WARNING"
                    }
                  }]
                }
                """;

        ReviewFinding finding = service.parseSemgrepJson(json, sourceDirectory).get(0);

        assertEquals("app.py", finding.getFile());
        assertNull(finding.getLine());
        assertEquals("Medium", finding.getSeverity());
        assertNull(finding.getExplanation());
        assertEquals(
                "No automatic fix provided; review the finding and determine an appropriate correction.",
                finding.getSuggestedFix());
    }

    @Test
    void parsesMultipleFindings() {
        String json = """
                {
                  "results": [
                    {"check_id": "first", "path": "one.java", "extra": {"severity": "INFO"}},
                    {"check_id": "second", "path": "nested/two.java", "extra": {"severity": "CRITICAL"}}
                  ]
                }
                """;

        List<ReviewFinding> findings = service.parseSemgrepJson(json, sourceDirectory);

        assertEquals(2, findings.size());
        assertEquals("one.java", findings.get(0).getFile());
        assertEquals("Low", findings.get(0).getSeverity());
        assertEquals("nested/two.java", findings.get(1).getFile());
        assertEquals("Critical", findings.get(1).getSeverity());
    }

    @Test
    void returnsNoFindingsForEmptyResults() {
        assertEquals(List.of(), service.parseSemgrepJson("{\"results\": []}", sourceDirectory));
    }

    @Test
    void rejectsInvalidJson() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.parseSemgrepJson("{invalid", sourceDirectory));

        assertEquals("Semgrep produced invalid JSON output.", exception.getMessage());
    }
}
