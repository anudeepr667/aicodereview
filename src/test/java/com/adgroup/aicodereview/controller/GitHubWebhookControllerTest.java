package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;
import com.adgroup.aicodereview.dto.WebhookDeliveryResponse;
import com.adgroup.aicodereview.service.GitHubWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitHubWebhookControllerTest {

    @Mock
    private GitHubWebhookService webhookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new GitHubWebhookController(webhookService)).build();
    }

    @Test
    void acceptsAValidatedWebhookWithHttp202() throws Exception {
        byte[] payload = "{\"action\":\"opened\"}".getBytes();
        when(webhookService.accept(eq("pull_request"), eq("delivery-1"), eq("sha256=signature"), any()))
                .thenReturn(new GitHubWebhookService.Acceptance(Status.RECEIVED, false));

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .header("X-Hub-Signature-256", "sha256=signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryId").value("delivery-1"))
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.duplicate").value(false));

        verify(webhookService).accept(
                eq("pull_request"), eq("delivery-1"), eq("sha256=signature"), any());
    }

    @Test
    void listsPersistedDeliveryStatusAndLinkedReviewWithoutInternalFailureDetails() throws Exception {
        when(webhookService.getRecentDeliveries()).thenReturn(List.of(new WebhookDeliveryResponse(
                "delivery-1",
                "owner/repo",
                "42",
                "pull_request",
                "opened",
                Status.FAILED,
                Instant.parse("2026-10-26T10:00:00Z"),
                17L,
                "Processing failed. Check repository access and the configured analysis services, then redeliver the webhook.")));

        mockMvc.perform(get("/api/webhooks/github/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repositoryName").value("owner/repo"))
                .andExpect(jsonPath("$[0].pullRequestNumber").value("42"))
                .andExpect(jsonPath("$[0].action").value("opened"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].reviewId").value(17))
                .andExpect(jsonPath("$[0].errorMessage").value(
                        "Processing failed. Check repository access and the configured analysis services, then redeliver the webhook."));

        verify(webhookService).getRecentDeliveries();
    }

    @Test
    void mapsInvalidSignatureToUnauthorized() throws Exception {
        when(webhookService.accept(any(), any(), any(), any()))
                .thenThrow(new GitHubWebhookService.InvalidSignatureException());

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .header("X-Hub-Signature-256", "sha256=bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    void mapsInvalidPayloadToBadRequest() throws Exception {
        when(webhookService.accept(any(), any(), any(), any()))
                .thenThrow(new GitHubWebhookService.InvalidWebhookRequestException("Malformed payload."));

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .header("X-Hub-Signature-256", "sha256=signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed payload."));
    }
}
