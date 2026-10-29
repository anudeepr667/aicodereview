package com.adgroup.aicodereview.dto;

import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;

import java.time.Instant;

public record WebhookDeliveryResponse(
        String deliveryId,
        String repositoryName,
        String pullRequestNumber,
        String eventType,
        String action,
        Status status,
        Instant deliveredAt,
        Long reviewId,
        String errorMessage) {
}
