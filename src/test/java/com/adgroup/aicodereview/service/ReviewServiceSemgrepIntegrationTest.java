package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.dto.AIReviewResult;
import com.adgroup.aicodereview.dto.CategoryReview;
import com.adgroup.aicodereview.dto.ReviewFinding;
import com.adgroup.aicodereview.github.GitHubService;
import com.adgroup.aicodereview.github.PullRequestDiff;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceSemgrepIntegrationTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private GitHubService gitHubService;
    @Mock
    private AIService aiService;
    @Mock
    private SemgrepAnalysisService semgrepAnalysisService;

    private ObjectMapper objectMapper;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reviewService = new ReviewService(
                reviewRepository,
                gitHubService,
                aiService,
                objectMapper,
                semgrepAnalysisService,
                5);
    }

    private void stubPipelinePrerequisites() {
        when(gitHubService.getPullRequestSnapshot("owner", "repo", "42", null))
                .thenReturn(snapshot());
    }

    private static GitHubService.PullRequestSnapshot snapshot() {
        String diff = """
                diff --git a/src/Main.java b/src/Main.java
                --- a/src/Main.java
                +++ b/src/Main.java
                @@ -7,0 +8,5 @@
                +line 8
                +line 9
                +line 10
                +line 11
                +line 12
                """;
        List<String> lines = new java.util.ArrayList<>();
        for (int line = 1; line <= 12; line++) {
            lines.add("line " + line);
        }
        String content = String.join("\n", lines) + "\n";
        PullRequestDiff.FileChanges changes = PullRequestDiff.parse(diff).files().getFirst();
        return new GitHubService.PullRequestSnapshot(
                diff,
                "base-sha",
                "head-sha",
                "merge-base-sha",
                List.of(new GitHubService.ChangedFileSnapshot(
                        changes, "line 1\n", content, true, true)));
    }

    private void stubReviewPersistence() {
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void combinesFindingsAndPreservesGeminiReviewMetadataAndCleansWorkspace() throws Exception {
        stubPipelinePrerequisites();
        stubReviewPersistence();
        ReviewFinding duplicateGeminiFinding = finding("src/Main.java", 8, "unsafe call");
        ReviewFinding geminiFinding = finding("src/Main.java", 10, "Gemini finding");
        ReviewFinding duplicateSemgrepFinding = finding("src\\Main.java", 8, "unsafe call");
        ReviewFinding distinctSemgrepFinding = finding("src/Main.java", 10, "Different problem");
        ReviewFinding semgrepFinding = finding("src/Main.java", 12, "Semgrep finding");
        duplicateGeminiFinding.setSeverity("Critical");
        geminiFinding.setSeverity("Low");
        duplicateSemgrepFinding.setSeverity("High");
        distinctSemgrepFinding.setSeverity("Warning");
        semgrepFinding.setSeverity("Error");

        AIReviewResult geminiResult = new AIReviewResult();
        geminiResult.setOverallScore(87);
        geminiResult.setSummary("Gemini summary");
        CategoryReview categoryReview = new CategoryReview();
        categoryReview.setCategory("Security");
        categoryReview.setReview("Looks good");
        geminiResult.setCategoryReviews(List.of(categoryReview));
        geminiResult.setPositiveAspects(List.of("Clear structure"));
        geminiResult.setFindings(List.of(duplicateGeminiFinding, geminiFinding));
        when(aiService.reviewCode(anyString())).thenReturn(geminiResult);

        Path[] scanDirectory = new Path[1];
        when(semgrepAnalysisService.analyze(any(Path.class))).thenAnswer(invocation -> {
            scanDirectory[0] = invocation.getArgument(0);
            assertEquals(12, Files.readAllLines(scanDirectory[0].resolve("src/Main.java")).size());
            return List.of(duplicateSemgrepFinding, distinctSemgrepFinding, semgrepFinding);
        });

        Review saved = reviewService.saveReview(newReview(), null);
        AIReviewResult stored = objectMapper.readValue(saved.getReviewText(), AIReviewResult.class);

        assertEquals(30, stored.getOverallScore());
        assertEquals("Gemini summary", stored.getSummary());
        assertEquals("Security", stored.getCategoryReviews().get(0).getCategory());
        assertEquals(List.of("Clear structure"), stored.getPositiveAspects());
        assertEquals(4, stored.getFindings().size());
        assertEquals("Different problem", stored.getFindings().get(1).getProblem());
        assertEquals("Gemini finding", stored.getFindings().get(2).getProblem());
        assertEquals("Semgrep finding", stored.getFindings().get(3).getProblem());
        assertEquals(List.of("Critical", "Medium", "Low", "High"),
                stored.getFindings().stream().map(ReviewFinding::getSeverity).toList());
        assertFalse(Files.exists(scanDirectory[0]));
    }

    @Test
    void persistsScoreFromHighestSeverityExactDuplicateFinding() throws Exception {
        stubPipelinePrerequisites();
        stubReviewPersistence();
        ReviewFinding geminiFinding = finding("src/Main.java", 8, "unsafe call");
        geminiFinding.setSeverity("Low");
        ReviewFinding semgrepFinding = finding("src\\Main.java", 8, "unsafe call");
        semgrepFinding.setSeverity("Critical");

        AIReviewResult geminiResult = new AIReviewResult();
        geminiResult.setFindings(List.of(geminiFinding));
        when(aiService.reviewCode(anyString())).thenReturn(geminiResult);
        when(semgrepAnalysisService.analyze(any(Path.class))).thenReturn(List.of(semgrepFinding));

        Review saved = reviewService.saveReview(newReview(), null);
        AIReviewResult stored = objectMapper.readValue(saved.getReviewText(), AIReviewResult.class);

        assertEquals(60, stored.getOverallScore());
        assertEquals(1, stored.getFindings().size());
        assertEquals("Critical", stored.getFindings().get(0).getSeverity());
    }

    @Test
    void runsSemgrepAndFirstGeminiPassConcurrently() {
        stubPipelinePrerequisites();
        stubReviewPersistence();
        CountDownLatch firstPassStarted = new CountDownLatch(1);
        CountDownLatch semgrepStarted = new CountDownLatch(1);
        AIReviewResult geminiResult = new AIReviewResult();
        geminiResult.setFindings(List.of());

        when(aiService.reviewCode(anyString())).thenAnswer(invocation -> {
            firstPassStarted.countDown();
            if (!semgrepStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Semgrep did not overlap the first Gemini pass.");
            }
            return geminiResult;
        });
        when(semgrepAnalysisService.analyze(any(Path.class))).thenAnswer(invocation -> {
            semgrepStarted.countDown();
            if (!firstPassStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Gemini did not overlap Semgrep.");
            }
            return List.of();
        });

        Review saved = reviewService.saveReview(newReview(), null);

        assertTrue(saved.getReviewText().contains("\"findings\":[]"));
        verify(aiService).reviewCode(anyString());
        verify(semgrepAnalysisService).analyze(any(Path.class));
    }

    @Test
    void omitsUnverifiedFindingsAndExplainsThatTheyWereNotIncluded() throws Exception {
        stubPipelinePrerequisites();
        stubReviewPersistence();
        AIReviewResult geminiResult = new AIReviewResult();
        geminiResult.setSummary("Review completed.");
        geminiResult.setFindings(List.of(
                finding("src/Main.java", 1, "Issue on an unchanged line"),
                finding("src/Elsewhere.java", 8, "Issue in an unrelated file")));
        when(aiService.reviewCode(anyString())).thenReturn(geminiResult);
        when(semgrepAnalysisService.analyze(any(Path.class))).thenReturn(List.of());

        Review saved = reviewService.saveReview(newReview(), null);
        AIReviewResult stored = objectMapper.readValue(saved.getReviewText(), AIReviewResult.class);

        assertEquals(List.of(), stored.getFindings());
        assertEquals(100, stored.getOverallScore());
        org.junit.jupiter.api.Assertions.assertTrue(
                stored.getSummary().contains("2 finding(s) were omitted"));
    }

    @Test
    void safelyCombinesNullFindingsAndSuccessfulEmptySemgrepResults() throws Exception {
        stubPipelinePrerequisites();
        stubReviewPersistence();
        AIReviewResult geminiResult = new AIReviewResult();
        geminiResult.setSummary("No Gemini findings");
        geminiResult.setFindings(null);
        when(aiService.reviewCode(anyString())).thenReturn(geminiResult);
        when(semgrepAnalysisService.analyze(any(Path.class))).thenReturn(List.of());

        Review saved = reviewService.saveReview(newReview(), null);
        AIReviewResult stored = objectMapper.readValue(saved.getReviewText(), AIReviewResult.class);

        assertEquals("No Gemini findings", stored.getSummary());
        assertEquals(List.of(), stored.getFindings());
    }

    @Test
    void safelyCombinesNullSemgrepFindings() throws Exception {
        stubPipelinePrerequisites();
        stubReviewPersistence();
        AIReviewResult geminiResult = new AIReviewResult();
        geminiResult.setFindings(List.of(finding("src/Main.java", 8, "Gemini only")));
        when(aiService.reviewCode(anyString())).thenReturn(geminiResult);
        when(semgrepAnalysisService.analyze(any(Path.class))).thenReturn(null);

        Review saved = reviewService.saveReview(newReview(), null);
        AIReviewResult stored = objectMapper.readValue(saved.getReviewText(), AIReviewResult.class);

        assertEquals(1, stored.getFindings().size());
        assertEquals("Gemini only", stored.getFindings().get(0).getProblem());
    }

    @Test
    void webhookReviewUsesPinnedHeadAndExistingAnalysisPipeline() throws Exception {
        String headSha = "0123456789abcdef0123456789abcdef01234567";
        String deliveryId = "webhook-delivery-42";
        when(reviewRepository.findByWebhookDeliveryId(deliveryId)).thenReturn(Optional.empty());
        when(gitHubService.getPullRequestSnapshot("owner", "repo", "42", headSha))
                .thenReturn(snapshot());
        when(semgrepAnalysisService.analyze(any(Path.class))).thenReturn(List.of());
        AIReviewResult aiResult = new AIReviewResult();
        aiResult.setSummary("Webhook review");
        aiResult.setFindings(List.of());
        when(aiService.reviewCode(anyString())).thenReturn(aiResult);
        stubReviewPersistence();

        Review review = newReview();
        review.setOwner("owner");
        Review saved = reviewService.saveWebhookReview(review, headSha, deliveryId);

        assertEquals(deliveryId, saved.getWebhookDeliveryId());
        assertNull(saved.getUser());
        verify(gitHubService).getPullRequestSnapshot("owner", "repo", "42", headSha);
    }

    @Test
    void sourceRetrievalFailureStopsReviewAndDoesNotSave() {
        when(gitHubService.getPullRequestSnapshot("owner", "repo", "42", null))
                .thenThrow(new IllegalStateException("GitHub source retrieval failed."));

        assertThrows(IllegalStateException.class, () -> reviewService.saveReview(newReview(), null));

        verify(semgrepAnalysisService, never()).analyze(any(Path.class));
        verify(aiService, never()).reviewCode(anyString());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void snapshotMismatchStopsReviewBeforeAnalysisOrPersistence() {
        when(gitHubService.getPullRequestSnapshot("owner", "repo", "42", null))
                .thenThrow(new IllegalStateException(
                        "Pull request base or head changed during analysis; retry the review."));

        assertThrows(IllegalStateException.class, () -> reviewService.saveReview(newReview(), null));

        verify(aiService, never()).reviewCode(anyString());
        verify(semgrepAnalysisService, never()).analyze(any(Path.class));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void semgrepFailureCleansWorkspaceAndStopsGeminiAndSave() {
        stubPipelinePrerequisites();
        Path[] scanDirectory = new Path[1];
        when(semgrepAnalysisService.analyze(any(Path.class))).thenAnswer(invocation -> {
            scanDirectory[0] = invocation.getArgument(0);
            throw new IllegalStateException("Semgrep analysis failed.");
        });

        assertThrows(IllegalStateException.class, () -> reviewService.saveReview(newReview(), null));

        assertFalse(Files.exists(scanDirectory[0]));
        verify(aiService).reviewCode(anyString());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void workspaceCleanupRemovesSourceFilesAndDirectory() throws IOException {
        Path workspace = Files.createTempDirectory("aicodereview-cleanup-test-");
        Files.writeString(workspace.resolve("source.java"), "class Source {}");
        ReviewService.deleteWorkspace(workspace);

        assertFalse(Files.exists(workspace));
    }

    private static Review newReview() {
        Review review = new Review();
        review.setRepositoryName("owner/repo");
        review.setPullRequestId("42");
        return review;
    }

    private static ReviewFinding finding(String file, int line, String problem) {
        ReviewFinding finding = new ReviewFinding();
        finding.setFile(file);
        finding.setLine(line);
        finding.setProblem(problem);
        return finding;
    }
}
