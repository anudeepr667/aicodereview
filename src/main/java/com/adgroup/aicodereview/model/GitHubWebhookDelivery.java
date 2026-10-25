package com.adgroup.aicodereview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "github_webhook_deliveries")
public class GitHubWebhookDelivery {

    public enum Status {
        RECEIVED,
        PROCESSING,
        COMPLETED,
        FAILED,
        IGNORED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String deliveryId;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(length = 40)
    private String action;

    @Column(length = 320)
    private String repositoryName;

    @Column(length = 20)
    private String pullRequestNumber;

    @Column(length = 64)
    private String headSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 4000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GitHubWebhookDelivery() {
    }

    public GitHubWebhookDelivery(
            String deliveryId,
            String eventType,
            String action,
            String repositoryName,
            String pullRequestNumber,
            String headSha,
            Status status) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.action = action;
        this.repositoryName = repositoryName;
        this.pullRequestNumber = pullRequestNumber;
        this.headSha = headSha;
        this.status = status;
        this.attempts = 0;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAction() {
        return action;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getPullRequestNumber() {
        return pullRequestNumber;
    }

    public String getHeadSha() {
        return headSha;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
