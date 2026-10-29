package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.model.GitHubWebhookDelivery;
import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.repository.GitHubWebhookDeliveryRepository;
import com.adgroup.aicodereview.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookServiceTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String DELIVERY_ID = "delivery-123";
    private static final String HEAD_SHA = "0123456789abcdef0123456789abcdef01234567";

    @Mock
    private GitHubWebhookDeliveryRepository deliveryRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewService reviewService;
    @Mock
    private TaskExecutor taskExecutor;

    private GitHubWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new GitHubWebhookService(
                new ObjectMapper(),
                SECRET,
                deliveryRepository,
                reviewRepository,
                reviewService,
                taskExecutor);
    }

    @Test
    void verifiesHmacSignatureAndRejectsInvalidSignatures() throws Exception {
        byte[] payload = payload("opened");
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.empty());
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubWebhookService.Acceptance accepted = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertEquals(Status.RECEIVED, accepted.status());
        assertFalse(accepted.duplicate());
        verify(taskExecutor).execute(any(Runnable.class));

        assertThrows(
                GitHubWebhookService.InvalidSignatureException.class,
                () -> webhookService.accept(
                        "pull_request", "invalid-signature-delivery", "sha256=" + "0".repeat(64), payload));
    }

    @Test
    void reportsPersistedStatusesAndLinksReviewsWithoutExposingStoredErrors() {
        GitHubWebhookDelivery failed = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "opened", "owner/repo", "42", HEAD_SHA, Status.FAILED);
        GitHubWebhookDelivery completed = new GitHubWebhookDelivery(
                "delivery-456", "pull_request", "synchronize", "owner/repo", "43", HEAD_SHA, Status.COMPLETED);
        Review savedReview = new Review();
        savedReview.setId(17L);
        savedReview.setWebhookDeliveryId("delivery-456");

        when(deliveryRepository.findTop100ByOrderByCreatedAtDesc())
                .thenReturn(List.of(failed, completed));
        when(reviewRepository.findByWebhookDeliveryIdIn(any()))
                .thenReturn(List.of(savedReview));

        var activity = webhookService.getRecentDeliveries();

        assertEquals(Status.FAILED, activity.getFirst().status());
        assertEquals("Processing failed. Check repository access and the configured analysis services, then redeliver the webhook.",
                activity.getFirst().errorMessage());
        assertEquals(Status.COMPLETED, activity.get(1).status());
        assertEquals(17L, activity.get(1).reviewId());
        assertEquals("delivery-456", activity.get(1).deliveryId());
    }

    @Test
    void failsClosedWhenWebhookSecretIsNotConfigured() {
        GitHubWebhookService missingSecretService = new GitHubWebhookService(
                new ObjectMapper(),
                "",
                deliveryRepository,
                reviewRepository,
                reviewService,
                taskExecutor);

        assertThrows(
                GitHubWebhookService.WebhookConfigurationException.class,
                () -> missingSecretService.accept(
                        "pull_request", DELIVERY_ID, "sha256=" + "0".repeat(64), payload("opened")));
        verify(deliveryRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMalformedJsonAndMalformedPullRequestFields() {
        byte[] malformedJson = "{broken".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                GitHubWebhookService.InvalidWebhookRequestException.class,
                () -> webhookService.accept(
                        "pull_request", DELIVERY_ID, signature(malformedJson), malformedJson));

        byte[] missingHead = """
                {"action":"opened","repository":{"full_name":"owner/repo"},
                 "pull_request":{"number":42,"head":{"sha":"invalid"}}}
                """.getBytes(StandardCharsets.UTF_8);
        assertThrows(
                GitHubWebhookService.InvalidWebhookRequestException.class,
                () -> webhookService.accept(
                        "pull_request", DELIVERY_ID, signature(missingHead), missingHead));
        verify(deliveryRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"opened", "reopened", "synchronize"})
    void acceptsSupportedPullRequestActions(String action) throws Exception {
        byte[] payload = payload(action);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.empty());
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubWebhookService.Acceptance acceptance = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertEquals(Status.RECEIVED, acceptance.status());
        verify(deliveryRepository).saveAndFlush(any(GitHubWebhookDelivery.class));
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void recordsUnsupportedActionsWithoutLaunchingAnalysis() throws Exception {
        byte[] payload = payload("closed");
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.empty());
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubWebhookService.Acceptance acceptance = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertEquals(Status.IGNORED, acceptance.status());
        verify(taskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void returnsExistingStateForDuplicateCompletedDelivery() throws Exception {
        byte[] payload = payload("opened");
        GitHubWebhookDelivery existing = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "opened", "owner/repo", "42", HEAD_SHA, Status.COMPLETED);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.of(existing));

        GitHubWebhookService.Acceptance acceptance = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertEquals(Status.COMPLETED, acceptance.status());
        assertTrue(acceptance.duplicate());
        verify(taskExecutor, never()).execute(any(Runnable.class));
        verify(deliveryRepository, never()).saveAndFlush(any());
    }

    @Test
    void reschedulesDurablyReceivedDeliveryToRecoverInterruptedEnqueue() throws Exception {
        byte[] payload = payload("opened");
        GitHubWebhookDelivery received = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "opened", "owner/repo", "42", HEAD_SHA, Status.RECEIVED);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.of(received));

        GitHubWebhookService.Acceptance acceptance = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertEquals(Status.RECEIVED, acceptance.status());
        assertTrue(acceptance.duplicate());
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void requeuesFailedDuplicateDeliveryForRetry() throws Exception {
        byte[] payload = payload("synchronize");
        GitHubWebhookDelivery failed = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "synchronize", "owner/repo", "42", HEAD_SHA, Status.FAILED);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.of(failed));
        when(deliveryRepository.transition(
                eq(DELIVERY_ID), eq(Status.FAILED), eq(Status.RECEIVED),
                eq(null), any(Instant.class))).thenReturn(1);

        GitHubWebhookService.Acceptance acceptance = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertEquals(Status.RECEIVED, acceptance.status());
        assertTrue(acceptance.duplicate());
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void processesAcceptedDeliveryAsynchronouslyAndCompletesIt() throws Exception {
        byte[] payload = payload("opened");
        GitHubWebhookDelivery delivery = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "opened", "owner/repo", "42", HEAD_SHA, Status.RECEIVED);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID))
                .thenReturn(Optional.empty(), Optional.of(delivery));
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.claim(
                eq(DELIVERY_ID), eq(Status.RECEIVED), eq(Status.PROCESSING), any(Instant.class)))
                .thenReturn(1);
        when(deliveryRepository.transition(
                eq(DELIVERY_ID), eq(Status.PROCESSING), eq(Status.COMPLETED),
                eq(null), any(Instant.class))).thenReturn(1);
        when(reviewRepository.findByWebhookDeliveryId(DELIVERY_ID)).thenReturn(Optional.empty());
        when(reviewService.saveWebhookReview(any(Review.class), eq(HEAD_SHA), eq(DELIVERY_ID)))
                .thenReturn(new Review());

        webhookService.accept("pull_request", DELIVERY_ID, signature(payload), payload);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(task.capture());
        verify(reviewService, never()).saveWebhookReview(any(), any(), any());

        task.getValue().run();

        verify(reviewService).saveWebhookReview(any(Review.class), eq(HEAD_SHA), eq(DELIVERY_ID));
        verify(deliveryRepository).transition(
                eq(DELIVERY_ID), eq(Status.PROCESSING), eq(Status.COMPLETED),
                eq(null), any(Instant.class));
    }

    @Test
    void recordsProcessingFailureForSafeRedeliveryRetry() throws Exception {
        byte[] payload = payload("opened");
        GitHubWebhookDelivery delivery = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "opened", "owner/repo", "42", HEAD_SHA, Status.RECEIVED);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID))
                .thenReturn(Optional.empty(), Optional.of(delivery));
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.claim(
                eq(DELIVERY_ID), eq(Status.RECEIVED), eq(Status.PROCESSING), any(Instant.class)))
                .thenReturn(1);
        when(reviewRepository.findByWebhookDeliveryId(DELIVERY_ID)).thenReturn(Optional.empty());
        when(reviewService.saveWebhookReview(any(Review.class), eq(HEAD_SHA), eq(DELIVERY_ID)))
                .thenThrow(new IllegalStateException("GitHub analysis failed."));
        when(deliveryRepository.transition(
                eq(DELIVERY_ID), eq(Status.PROCESSING), eq(Status.FAILED),
                eq("GitHub analysis failed."), any(Instant.class))).thenReturn(1);

        webhookService.accept("pull_request", DELIVERY_ID, signature(payload), payload);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(task.capture());
        task.getValue().run();

        verify(deliveryRepository).transition(
                eq(DELIVERY_ID), eq(Status.PROCESSING), eq(Status.FAILED),
                eq("GitHub analysis failed."), any(Instant.class));
    }

    @Test
    void handlesDeliveryIdConflictFromConcurrentInsert() throws Exception {
        byte[] payload = payload("opened");
        GitHubWebhookDelivery existing = new GitHubWebhookDelivery(
                DELIVERY_ID, "pull_request", "opened", "owner/repo", "42", HEAD_SHA, Status.COMPLETED);
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        GitHubWebhookService.Acceptance acceptance = webhookService.accept(
                "pull_request", DELIVERY_ID, signature(payload), payload);

        assertTrue(acceptance.duplicate());
        assertEquals(Status.COMPLETED, acceptance.status());
        verify(taskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void marksDeliveryFailedIfAsyncQueueRejectsIt() throws Exception {
        byte[] payload = payload("opened");
        when(deliveryRepository.findByDeliveryId(DELIVERY_ID)).thenReturn(Optional.empty());
        when(deliveryRepository.saveAndFlush(any(GitHubWebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("queue full"))
                .when(taskExecutor).execute(any(Runnable.class));
        when(deliveryRepository.transition(
                eq(DELIVERY_ID), eq(Status.RECEIVED), eq(Status.FAILED),
                eq("Unable to enqueue webhook processing."), any(Instant.class))).thenReturn(1);

        assertThrows(
                GitHubWebhookService.WebhookSchedulingException.class,
                () -> webhookService.accept(
                        "pull_request", DELIVERY_ID, signature(payload), payload));

        verify(deliveryRepository).transition(
                eq(DELIVERY_ID), eq(Status.RECEIVED), eq(Status.FAILED),
                eq("Unable to enqueue webhook processing."), any(Instant.class));
    }

    private static byte[] payload(String action) {
        return """
                {
                  "action": "%s",
                  "repository": {"full_name": "owner/repo"},
                  "pull_request": {
                    "number": 42,
                    "head": {"sha": "%s"}
                  }
                }
                """.formatted(action, HEAD_SHA).getBytes(StandardCharsets.UTF_8);
    }

    private static String signature(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
