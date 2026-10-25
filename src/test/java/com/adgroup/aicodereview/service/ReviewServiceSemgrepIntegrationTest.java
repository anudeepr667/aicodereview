package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.dto.AIReviewResult;
import com.adgroup.aicodereview.dto.CategoryReview;
import com.adgroup.aicodereview.dto.ReviewFinding;
import com.adgroup.aicodereview.github.GitHubService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(gitHubService.getPullRequestDiff("owner", "repo", "42"))
                .thenReturn("diff --git a/src/Main.java b/src/Main.java");
        when(gitHubService.getPullRequestSourceFiles("owner", "repo", "42"))
                .thenReturn(List.of(new GitHubService.SourceFile("src/Main.java", "class Main {}")));
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
            assertEquals("class Main {}", Files.readString(scanDirectory[0].resolve("src/Main.java")));
            return List.of(duplicateSemgrepFinding, distinctSemgrepFinding, semgrepFinding);
        });

        Review saved = reviewService.saveReview(newReview(), null);
        AIReviewResult stored = objectMapper.readValue(saved.getReviewText(), AIReviewResult.class);

        assertEquals(87, stored.getOverallScore());
        assertEquals("Gemini summary", stored.getSummary());
        assertEquals("Security", stored.getCategoryReviews().get(0).getCategory());
        assertEquals(List.of("Clear structure"), stored.getPositiveAspects());
        assertEquals(4, stored.getFindings().size());
        assertEquals("Different problem", stored.getFindings().get(1).getProblem());
        assertEquals("Gemini finding", stored.getFindings().get(2).getProblem());
        assertEquals("Semgrep finding", stored.getFindings().get(3).getProblem());
        assertFalse(Files.exists(scanDirectory[0]));
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
        geminiResult.setFindings(List.of(finding("src/Main.java", 4, "Gemini only")));
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
        when(gitHubService.getPullRequestDiff("owner", "repo", "42", headSha))
                .thenReturn("diff --git a/src/Main.java b/src/Main.java");
        when(gitHubService.getPullRequestSourceFiles("owner", "repo", "42", headSha))
                .thenReturn(List.of(new GitHubService.SourceFile("src/Main.java", "class Main {}")));
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
        verify(gitHubService).getPullRequestDiff("owner", "repo", "42", headSha);
        verify(gitHubService).getPullRequestSourceFiles("owner", "repo", "42", headSha);
        verify(gitHubService, never()).getPullRequestDiff("owner", "repo", "42");
        verify(gitHubService, never()).getPullRequestSourceFiles("owner", "repo", "42");
    }

    @Test
    void sourceRetrievalFailureStopsReviewAndDoesNotSave() {
        when(gitHubService.getPullRequestDiff("owner", "repo", "42"))
                .thenReturn("diff --git a/src/Main.java b/src/Main.java");
        when(gitHubService.getPullRequestSourceFiles("owner", "repo", "42"))
                .thenThrow(new IllegalStateException("GitHub source retrieval failed."));

        assertThrows(IllegalStateException.class, () -> reviewService.saveReview(newReview(), null));

        verify(semgrepAnalysisService, never()).analyze(any(Path.class));
        verify(aiService, never()).reviewCode(anyString());
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
        verify(aiService, never()).reviewCode(anyString());
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
