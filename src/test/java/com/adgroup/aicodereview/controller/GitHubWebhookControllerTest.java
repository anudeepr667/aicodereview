package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;
import com.adgroup.aicodereview.service.GitHubWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
