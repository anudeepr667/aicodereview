package com.adgroup.aicodereview.repository;

import com.adgroup.aicodereview.model.GitHubWebhookDelivery;
import com.adgroup.aicodereview.model.GitHubWebhookDelivery.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface GitHubWebhookDeliveryRepository
        extends JpaRepository<GitHubWebhookDelivery, Long> {

    Optional<GitHubWebhookDelivery> findByDeliveryId(String deliveryId);

    @Modifying
    @Transactional
    @Query("""
            update GitHubWebhookDelivery delivery
            set delivery.status = :target,
                delivery.errorMessage = :error,
                delivery.updatedAt = :updatedAt
            where delivery.deliveryId = :deliveryId
              and delivery.status = :expected
            """)
    int transition(
            @Param("deliveryId") String deliveryId,
            @Param("expected") Status expected,
            @Param("target") Status target,
            @Param("error") String error,
            @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Transactional
    @Query("""
            update GitHubWebhookDelivery delivery
            set delivery.status = :processing,
                delivery.attempts = delivery.attempts + 1,
                delivery.errorMessage = null,
                delivery.updatedAt = :updatedAt
            where delivery.deliveryId = :deliveryId
              and delivery.status = :received
            """)
    int claim(
            @Param("deliveryId") String deliveryId,
            @Param("received") Status received,
            @Param("processing") Status processing,
            @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Transactional
    @Query("""
            update GitHubWebhookDelivery delivery
            set delivery.status = :received,
                delivery.errorMessage = :error,
                delivery.updatedAt = :updatedAt
            where delivery.deliveryId = :deliveryId
              and delivery.status = :processing
              and delivery.updatedAt < :staleBefore
            """)
    int requeueStaleProcessing(
            @Param("deliveryId") String deliveryId,
            @Param("processing") Status processing,
            @Param("received") Status received,
            @Param("error") String error,
            @Param("staleBefore") Instant staleBefore,
            @Param("updatedAt") Instant updatedAt);
}
