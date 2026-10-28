package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.dto.ReviewFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindingDeduplicationTest {

    private final AIService aiService = mock(AIService.class);

    @Test
    void removesExactCrossAnalyzerFinding() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Unsafe SQL query");
        ReviewFinding semgrep = finding("src\\Main.java", 12, "Unsafe SQL query");

        assertEquals(List.of(gemini), combine(List.of(gemini), List.of(semgrep)));
        verify(aiService, never()).areDuplicateFindings(gemini, semgrep);
    }

    @Test
    void keepsHighestSeverityAndMergesEvidenceForExactDuplicateFindings() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Unsafe SQL query");
        gemini.setSeverity("Low");
        gemini.setExplanation("Gemini evidence");
        ReviewFinding semgrep = finding("src\\Main.java", 12, "Unsafe SQL query");
        semgrep.setSeverity("Critical");
        semgrep.setSuggestedFix("Use a parameterized query.");

        List<ReviewFinding> combined = combine(List.of(gemini), List.of(semgrep));

        assertEquals(1, combined.size());
        assertEquals("Critical", combined.get(0).getSeverity());
        assertEquals("Gemini evidence", combined.get(0).getExplanation());
        assertEquals("Use a parameterized query.", combined.get(0).getSuggestedFix());
        assertEquals(60, ReviewService.calculateScore(combined));
        verify(aiService, never()).areDuplicateFindings(gemini, semgrep);
    }

    @Test
    void keepsHighestSeverityWhenGeminiFindingIsMoreSevere() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Unsafe SQL query");
        gemini.setSeverity("Critical");
        ReviewFinding semgrep = finding("src/Main.java", 12, "Unsafe SQL query");
        semgrep.setSeverity("Low");

        List<ReviewFinding> combined = combine(List.of(gemini), List.of(semgrep));

        assertEquals(1, combined.size());
        assertEquals("Critical", combined.get(0).getSeverity());
        assertEquals(60, ReviewService.calculateScore(combined));
    }

    @Test
    void mergesDistinctExplanationsAndSuggestedFixesWithoutDiscardingEither() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Unsafe SQL query");
        gemini.setExplanation("Gemini evidence");
        gemini.setSuggestedFix("Validate input.");
        ReviewFinding semgrep = finding("src/Main.java", 12, "Unsafe SQL query");
        semgrep.setExplanation("Semgrep evidence");
        semgrep.setSuggestedFix("Use a parameterized query.");

        ReviewFinding merged = combine(List.of(gemini), List.of(semgrep)).get(0);

        assertTrue(merged.getExplanation().contains("Gemini evidence"));
        assertTrue(merged.getExplanation().contains("Semgrep evidence"));
        assertTrue(merged.getSuggestedFix().contains("Validate input."));
        assertTrue(merged.getSuggestedFix().contains("Use a parameterized query."));
    }

    @Test
    void removesFindingsDifferingOnlyByWhitespaceCapitalizationAndPunctuation() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Unsafe SQL query.");
        ReviewFinding semgrep = finding("src\\Main.java", 12, "  UNSAFE   SQL QUERY! ");

        assertEquals(List.of(gemini), combine(List.of(gemini), List.of(semgrep)));
        verify(aiService, never()).areDuplicateFindings(gemini, semgrep);
    }

    @Test
    void preservesFindingsFromPathsThatDifferOnlyByCase() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Unsafe SQL query");
        ReviewFinding semgrep = finding("src/main.java", 12, "Unsafe SQL query");

        assertEquals(2, combine(List.of(gemini), List.of(semgrep)).size());
        verify(aiService, never()).areDuplicateFindings(gemini, semgrep);
    }

    @Test
    void removesDifferentWordingWhenSemanticComparisonConfirmsSameRootCause() {
        ReviewFinding gemini = finding(
                "src/Main.java", 12,
                "User-controlled input is interpolated into a SQL statement without parameterization.");
        ReviewFinding semgrep = finding("src/Main.java", 12, "Use of an unsafe SQL query.");
        when(aiService.areDuplicateFindings(gemini, semgrep)).thenReturn(true);

        assertEquals(List.of(gemini), combine(List.of(gemini), List.of(semgrep)));
    }

    @Test
    void retainsDistinctIssuesReportedOnSameLine() {
        ReviewFinding gemini = finding("src/Main.java", 12, "SQL input is not parameterized");
        ReviewFinding semgrep = finding("src/Main.java", 12, "Sensitive value is logged");
        gemini.setCategory("Security");
        semgrep.setCategory("Security");
        when(aiService.areDuplicateFindings(gemini, semgrep)).thenReturn(false);

        assertEquals(2, combine(List.of(gemini), List.of(semgrep)).size());
    }

    @Test
    void retainsFindingsInDifferentFilesWithoutSemanticComparison() {
        ReviewFinding gemini = finding("src/First.java", 12, "Unsafe SQL query");
        ReviewFinding semgrep = finding("src/Second.java", 12, "Unsafe SQL query");

        assertEquals(2, combine(List.of(gemini), List.of(semgrep)).size());
        verify(aiService, never()).areDuplicateFindings(gemini, semgrep);
    }

    @Test
    void retainsDifferentRootCausesInTheSameCategory() {
        ReviewFinding gemini = finding("src/Main.java", 12, "SQL input is not parameterized");
        ReviewFinding semgrep = finding("src/Main.java", 12, "Sensitive value is logged");
        gemini.setCategory("Security");
        semgrep.setCategory("Security");
        when(aiService.areDuplicateFindings(gemini, semgrep)).thenReturn(false);

        assertEquals(2, combine(List.of(gemini), List.of(semgrep)).size());
    }

    @Test
    void preservesUniqueFindingsFromBothAnalyzers() {
        ReviewFinding gemini = finding("src/Main.java", 3, "Gemini-only finding");
        ReviewFinding semgrep = finding("src/Main.java", 30, "Semgrep-only finding");

        assertEquals(2, combine(List.of(gemini), List.of(semgrep)).size());
    }

    @Test
    void handlesNullAndEmptyFindingLists() {
        assertEquals(List.of(), combine(null, null));
        assertEquals(List.of(), combine(List.of(), null));
        assertEquals(List.of(), combine(null, List.of()));
    }

    @Test
    void retainsUncertainFindingsWhenSemanticComparisonFails() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Potential unsafe SQL");
        ReviewFinding semgrep = finding("src/Main.java", 12, "SQL query risk");
        when(aiService.areDuplicateFindings(gemini, semgrep))
                .thenThrow(new IllegalStateException("Comparison unavailable"));

        assertEquals(2, combine(List.of(gemini), List.of(semgrep)).size());
    }

    @Test
    void enforcesMaximumSemanticComparisonCount() {
        ReviewFinding gemini = finding("src/Main.java", 12, "Potential unsafe SQL");
        List<ReviewFinding> semgrep = List.of(
                finding("src/Main.java", 11, "SQL query risk one"),
                finding("src/Main.java", 12, "SQL query risk two"),
                finding("src/Main.java", 13, "SQL query risk three"));
        when(aiService.areDuplicateFindings(gemini, semgrep.get(0))).thenReturn(false);
        when(aiService.areDuplicateFindings(gemini, semgrep.get(1))).thenReturn(false);

        assertEquals(4, ReviewService.combineFindings(List.of(gemini), semgrep, aiService, 2).size());
        verify(aiService, times(1)).areDuplicateFindings(gemini, semgrep.get(0));
        verify(aiService, times(1)).areDuplicateFindings(gemini, semgrep.get(1));
        verify(aiService, never()).areDuplicateFindings(gemini, semgrep.get(2));
    }

    private List<ReviewFinding> combine(
            List<ReviewFinding> geminiFindings,
            List<ReviewFinding> semgrepFindings) {
        return ReviewService.combineFindings(geminiFindings, semgrepFindings, aiService, 5);
    }

    private static ReviewFinding finding(String file, int line, String problem) {
        ReviewFinding finding = new ReviewFinding();
        finding.setFile(file);
        finding.setLine(line);
        finding.setProblem(problem);
        return finding;
    }
}
