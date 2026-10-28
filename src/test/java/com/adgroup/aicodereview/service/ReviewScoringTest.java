package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewScoringTest {

    @Test
    void cleanReviewScoresOneHundred() {
        assertEquals(100, ReviewService.calculateScore(List.of()));
    }

    @Test
    void appliesPenaltyForEachRecognizedSeverity() {
        assertEquals(60, score("Critical"));
        assertEquals(80, score("High"));
        assertEquals(92, score("Medium"));
        assertEquals(98, score("Low"));
    }

    @Test
    void accumulatesPenaltiesForMultipleFindings() {
        assertEquals(30, ReviewService.calculateScore(List.of(
                finding("src/A.java", 1, "critical issue", "Critical"),
                finding("src/A.java", 2, "high issue", "High"),
                finding("src/A.java", 3, "medium issue", "Medium"),
                finding("src/A.java", 4, "low issue", "Low"))));
    }

    @Test
    void countsNormalizedExactFindingIdentityOnlyOnce() {
        assertEquals(60, ReviewService.calculateScore(List.of(
                finding("src/Main.java", 12, "Unsafe SQL query.", "High"),
                finding("src\\Main.java", 12, " UNSAFE   SQL QUERY! ", "Critical"))));
    }

    @Test
    void countsFindingsWithMissingLineAndProblemSeparately() {
        ReviewFinding first = finding("src/Main.java", null, null, "High");
        ReviewFinding second = finding("src/Main.java", null, null, "High");

        assertEquals(60, ReviewService.calculateScore(List.of(first, second)));
    }

    @Test
    void countsAllIncompleteIdentitiesSeparately() {
        ReviewFinding missingLine = finding("src/Main.java", null, "same issue", "High");
        ReviewFinding missingProblem = finding("src/Main.java", 12, null, "High");
        ReviewFinding blankProblem = finding("src/Main.java", 12, "   ", "High");

        assertEquals(40, ReviewService.calculateScore(
                List.of(missingLine, missingProblem, blankProblem)));
    }

    @Test
    void preservesDistinctIssuesOnTheSameLine() {
        assertEquals(60, ReviewService.calculateScore(List.of(
                finding("src/Main.java", 12, "SQL input is not parameterized", "High"),
                finding("src/Main.java", 12, "Sensitive value is logged", "High"))));
    }

    @Test
    void mapsRecognizedAliasesAndUnknownSeverityToCanonicalLabels() {
        ReviewFinding error = finding("src/A.java", 1, "error label", " eRrOr ");
        ReviewFinding warning = finding("src/A.java", 2, "warning label", "WARNING");
        ReviewFinding info = finding("src/A.java", 3, "info label", "info");
        ReviewFinding unknown = finding("src/A.java", 4, "unknown label", "Blocker");
        ReviewFinding missing = finding("src/A.java", 5, "missing label", null);

        assertEquals(54, ReviewService.calculateScore(
                List.of(error, warning, info, unknown, missing)));
        assertEquals("High", error.getSeverity());
        assertEquals("Medium", warning.getSeverity());
        assertEquals("Low", info.getSeverity());
        assertEquals("Medium", unknown.getSeverity());
        assertEquals("Medium", missing.getSeverity());
    }

    @Test
    void floorsScoreAtZero() {
        List<ReviewFinding> findings = java.util.stream.IntStream.range(0, 3)
                .mapToObj(index -> finding(
                        "src/A.java",
                        index + 1,
                        "critical issue " + index,
                        "Critical"))
                .toList();

        assertEquals(0, ReviewService.calculateScore(findings));
    }

    private static double score(String severity) {
        return ReviewService.calculateScore(
                List.of(finding("src/A.java", 1, "issue", severity)));
    }

    private static ReviewFinding finding(
            String file,
            Integer line,
            String problem,
            String severity) {
        ReviewFinding finding = new ReviewFinding();
        finding.setFile(file);
        finding.setLine(line);
        finding.setProblem(problem);
        finding.setSeverity(severity);
        return finding;
    }
}
