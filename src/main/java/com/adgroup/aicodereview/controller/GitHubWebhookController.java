package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.dto.WebhookDeliveryResponse;
import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;
import com.adgroup.aicodereview.service.GitHubWebhookService;
import com.adgroup.aicodereview.service.GitHubWebhookService.InvalidSignatureException;
import com.adgroup.aicodereview.service.GitHubWebhookService.InvalidWebhookRequestException;
import com.adgroup.aicodereview.service.GitHubWebhookService.WebhookConfigurationException;
import com.adgroup.aicodereview.service.GitHubWebhookService.WebhookSchedulingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping("/github/deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WebhookDeliveryResponse> getRecentDeliveries() {
        return webhookService.getRecentDeliveries();
    }

    @PostMapping("/github")
    public ResponseEntity<Map<String, Object>> receiveGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] payload) {
        try {
            GitHubWebhookService.Acceptance acceptance =
                    webhookService.accept(eventType, deliveryId, signature, payload);
            return ResponseEntity.accepted().body(Map.of(
                    "deliveryId", deliveryId,
                    "status", acceptance.status().name().toLowerCase(Locale.ROOT),
                    "duplicate", acceptance.duplicate()));
        } catch (InvalidSignatureException e) {
            return error(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (InvalidWebhookRequestException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (WebhookConfigurationException | WebhookSchedulingException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", Status.FAILED.name().toLowerCase(Locale.ROOT),
                "message", message));
    }
}
