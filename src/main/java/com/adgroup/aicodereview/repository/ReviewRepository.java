package com.adgroup.aicodereview.repository;

import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByOrderByIdDesc();

    List<Review> findByUserOrderByIdDesc(User user);

    Optional<Review> findByWebhookDeliveryId(String webhookDeliveryId);

    List<Review> findByWebhookDeliveryIdIn(Collection<String> webhookDeliveryIds);

}