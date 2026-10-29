package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.WebhookDeliveryResponse;
import com.adgroup.aicodereview.model.GitHubWebhookDelivery;
import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.repository.GitHubWebhookDeliveryRepository;
import com.adgroup.aicodereview.repository.ReviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class GitHubWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final Pattern DELIVERY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern ACTION_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Pattern OWNER_OR_REPOSITORY_PATTERN =
            Pattern.compile("[A-Za-z0-9_.-]{1,100}");
    private static final Pattern SHA_PATTERN = Pattern.compile("(?i)[0-9a-f]{40,64}");
    private static final Duration STALE_PROCESSING_AGE = Duration.ofMinutes(15);
    private static final Set<String> SUPPORTED_ACTIONS =
            Set.of("opened", "reopened", "synchronize");

    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final GitHubWebhookDeliveryRepository deliveryRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final TaskExecutor taskExecutor;

    public GitHubWebhookService(
            ObjectMapper objectMapper,
            @Value("${GITHUB_WEBHOOK_SECRET:}") String webhookSecret,
            GitHubWebhookDeliveryRepository deliveryRepository,
            ReviewRepository reviewRepository,
            ReviewService reviewService,
            TaskExecutor githubWebhookTaskExecutor) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
        this.deliveryRepository = deliveryRepository;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.taskExecutor = githubWebhookTaskExecutor;
    }

    public Acceptance accept(
            String eventType,
            String deliveryId,
            String signature,
            byte[] payload) {
        verifySignature(signature, payload);
        if (eventType == null || !EVENT_TYPE_PATTERN.matcher(eventType).matches()) {
            throw new InvalidWebhookRequestException("Missing or invalid GitHub event type.");
        }
        if (deliveryId == null || !DELIVERY_ID_PATTERN.matcher(deliveryId).matches()) {
            throw new InvalidWebhookRequestException("Missing or invalid GitHub delivery ID.");
        }

        JsonNode root = parsePayload(payload);
        GitHubWebhookDelivery delivery = createDelivery(eventType, deliveryId, root);
        Optional<GitHubWebhookDelivery> existing =
                deliveryRepository.findByDeliveryId(deliveryId);
        if (existing.isPresent()) {
            return handleDuplicate(existing.get(), delivery);
        }

        try {
            deliveryRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException duplicateInsert) {
            Optional<GitHubWebhookDelivery> concurrentDelivery =
                    deliveryRepository.findByDeliveryId(deliveryId);
            if (concurrentDelivery.isEmpty()) {
                throw duplicateInsert;
            }
            return handleDuplicate(concurrentDelivery.get(), delivery);
        }

        if (delivery.getStatus() == Status.RECEIVED) {
            schedule(deliveryId);
        }
        return new Acceptance(delivery.getStatus(), false);
    }

    public List<WebhookDeliveryResponse> getRecentDeliveries() {
        List<GitHubWebhookDelivery> deliveries =
                deliveryRepository.findTop100ByOrderByCreatedAtDesc();
        Set<String> deliveryIds = deliveries.stream()
                .map(GitHubWebhookDelivery::getDeliveryId)
                .collect(Collectors.toSet());
        Map<String, Long> reviewIds = deliveryIds.isEmpty()
                ? Map.of()
                : reviewRepository.findByWebhookDeliveryIdIn(deliveryIds).stream()
                        .collect(Collectors.toMap(
                                Review::getWebhookDeliveryId,
                                Review::getId));

        return deliveries.stream()
                .map(delivery -> new WebhookDeliveryResponse(
                        delivery.getDeliveryId(),
                        delivery.getRepositoryName(),
                        delivery.getPullRequestNumber(),
                        delivery.getEventType(),
                        delivery.getAction(),
                        delivery.getStatus(),
                        delivery.getCreatedAt(),
                        reviewIds.get(delivery.getDeliveryId()),
                        delivery.getStatus() == Status.FAILED
                                ? "Processing failed. Check repository access and the configured analysis services, then redeliver the webhook."
                                : null))
                .toList();
    }

    private void verifySignature(String providedSignature, byte[] payload) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new WebhookConfigurationException(
                    "GitHub webhook is disabled because GITHUB_WEBHOOK_SECRET is not configured.");
        }
        if (payload == null || providedSignature == null
                || !providedSignature.matches("(?i)sha256=[0-9a-f]{64}")) {
            throw new InvalidSignatureException();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = ("sha256=" + HexFormat.of().formatHex(mac.doFinal(payload)))
                    .getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(
                    expected,
                    providedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                throw new InvalidSignatureException();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to verify GitHub webhook signature.", e);
        }
    }

    private JsonNode parsePayload(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new InvalidWebhookRequestException("GitHub webhook payload must be a JSON object.");
            }
            return root;
        } catch (java.io.IOException e) {
            throw new InvalidWebhookRequestException("GitHub webhook payload is not valid JSON.");
        }
    }

    private GitHubWebhookDelivery createDelivery(
            String eventType,
            String deliveryId,
            JsonNode root) {
        if (!"pull_request".equals(eventType)) {
            return new GitHubWebhookDelivery(
                    deliveryId, eventType, "ignored", null, null, null, Status.IGNORED);
        }

        String action = text(root.path("action"));
        if (action == null || !ACTION_PATTERN.matcher(action).matches()) {
            throw new InvalidWebhookRequestException(
                    "Pull request webhook is missing or has an invalid action.");
        }
        if (!SUPPORTED_ACTIONS.contains(action)) {
            return new GitHubWebhookDelivery(
                    deliveryId, eventType, action, null, null, null, Status.IGNORED);
        }

        String fullName = text(root.path("repository").path("full_name"));
        String pullNumber = positivePullNumber(root.path("pull_request").path("number"));
        String headSha = text(root.path("pull_request").path("head").path("sha"));
        if (!isValidRepositoryName(fullName)) {
            throw new InvalidWebhookRequestException(
                    "Pull request webhook has an invalid repository name.");
        }
        if (pullNumber == null) {
            throw new InvalidWebhookRequestException(
                    "Pull request webhook has an invalid pull request number.");
        }
        if (headSha == null || !SHA_PATTERN.matcher(headSha).matches()) {
            throw new InvalidWebhookRequestException(
                    "Pull request webhook has an invalid head SHA.");
        }
        return new GitHubWebhookDelivery(
                deliveryId,
                eventType,
                action,
                fullName,
                pullNumber,
                headSha.toLowerCase(Locale.ROOT),
                Status.RECEIVED);
    }

    private Acceptance handleDuplicate(
            GitHubWebhookDelivery existing,
            GitHubWebhookDelivery incoming) {
        if (!sameDelivery(existing, incoming)) {
            throw new InvalidWebhookRequestException(
                    "GitHub delivery ID was already used for a different event.");
        }
        if (existing.getStatus() == Status.RECEIVED) {
            schedule(existing.getDeliveryId());
            return new Acceptance(Status.RECEIVED, true);
        }
        if (existing.getStatus() == Status.FAILED
                && deliveryRepository.transition(
                        existing.getDeliveryId(),
                        Status.FAILED,
                        Status.RECEIVED,
                        null,
                        Instant.now()) == 1) {
            schedule(existing.getDeliveryId());
            return new Acceptance(Status.RECEIVED, true);
        }
        Instant staleBefore = Instant.now().minus(STALE_PROCESSING_AGE);
        if (existing.getStatus() == Status.PROCESSING
                && existing.getUpdatedAt() != null
                && existing.getUpdatedAt().isBefore(staleBefore)
                && deliveryRepository.requeueStaleProcessing(
                        existing.getDeliveryId(),
                        Status.PROCESSING,
                        Status.RECEIVED,
                        "Requeued after an interrupted processing attempt.",
                        staleBefore,
                        Instant.now()) == 1) {
            schedule(existing.getDeliveryId());
            return new Acceptance(Status.RECEIVED, true);
        }
        return new Acceptance(existing.getStatus(), true);
    }

    private static boolean sameDelivery(
            GitHubWebhookDelivery existing,
            GitHubWebhookDelivery incoming) {
        return existing.getEventType().equals(incoming.getEventType())
                && equalNullable(existing.getAction(), incoming.getAction())
                && equalNullable(existing.getRepositoryName(), incoming.getRepositoryName())
                && equalNullable(existing.getPullRequestNumber(), incoming.getPullRequestNumber())
                && equalNullable(existing.getHeadSha(), incoming.getHeadSha());
    }

    private static boolean equalNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private void schedule(String deliveryId) {
        try {
            taskExecutor.execute(() -> process(deliveryId));
        } catch (RuntimeException e) {
            deliveryRepository.transition(
                    deliveryId, Status.RECEIVED, Status.FAILED,
                    "Unable to enqueue webhook processing.", Instant.now());
            throw new WebhookSchedulingException(
                    "Webhook was recorded but could not be queued for processing.", e);
        }
    }

    private void process(String deliveryId) {
        if (deliveryRepository.claim(
                deliveryId, Status.RECEIVED, Status.PROCESSING, Instant.now()) != 1) {
            return;
        }
        try {
            GitHubWebhookDelivery delivery =
                    deliveryRepository.findByDeliveryId(deliveryId).orElseThrow();
            Optional<Review> savedReview = reviewRepository.findByWebhookDeliveryId(deliveryId);
            if (savedReview.isEmpty()) {
                String[] repositoryParts = delivery.getRepositoryName().split("/", 2);
                Review review = new Review();
                review.setOwner(repositoryParts[0]);
                review.setRepositoryName(delivery.getRepositoryName());
                review.setPullRequestId(delivery.getPullRequestNumber());
                reviewService.saveWebhookReview(review, delivery.getHeadSha(), deliveryId);
            }
            deliveryRepository.transition(
                    deliveryId, Status.PROCESSING, Status.COMPLETED, null, Instant.now());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            if (message.length() > 4000) {
                message = message.substring(0, 4000);
            }
            try {
                deliveryRepository.transition(
                        deliveryId, Status.PROCESSING, Status.FAILED, message, Instant.now());
            } catch (RuntimeException statusFailure) {
                e.addSuppressed(statusFailure);
            }
            LOGGER.error("GitHub webhook processing failed for delivery {}.", deliveryId, e);
        }
    }

    private static boolean isValidRepositoryName(String fullName) {
        if (fullName == null) {
            return false;
        }
        String[] parts = fullName.split("/", -1);
        return parts.length == 2
                && OWNER_OR_REPOSITORY_PATTERN.matcher(parts[0]).matches()
                && OWNER_OR_REPOSITORY_PATTERN.matcher(parts[1]).matches();
    }

    private static String positivePullNumber(JsonNode node) {
        if (!node.canConvertToLong()) {
            return null;
        }
        long number = node.longValue();
        return number > 0 ? Long.toString(number) : null;
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    public record Acceptance(Status status, boolean duplicate) {
    }

    public static class InvalidSignatureException extends RuntimeException {
        public InvalidSignatureException() {
            super("Invalid GitHub webhook signature.");
        }
    }

    public static class InvalidWebhookRequestException extends RuntimeException {
        public InvalidWebhookRequestException(String message) {
            super(message);
        }
    }

    public static class WebhookConfigurationException extends RuntimeException {
        public WebhookConfigurationException(String message) {
            super(message);
        }
    }

    public static class WebhookSchedulingException extends RuntimeException {
        public WebhookSchedulingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
