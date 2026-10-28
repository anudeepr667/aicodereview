package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.dto.ReviewRequest;
import com.adgroup.aicodereview.dto.ReviewResponse;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.model.User;
import com.adgroup.aicodereview.service.ReviewService;
import com.adgroup.aicodereview.service.ReviewTiming;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/review")
    public Review getReview(@AuthenticationPrincipal User user) {
        return reviewService.saveSampleReview(user);
    }

    @PostMapping("/review")
    public ReviewResponse createReview(@RequestBody ReviewRequest request, @AuthenticationPrincipal User user) {

        String previousCorrelationId = ReviewTiming.setCorrelationId(ReviewTiming.correlationId());
        try {
            Review review = ReviewTiming.measure("review_controller_preparation", () -> {
                Review requestedReview = new Review();
                requestedReview.setOwner(request.getOwner());
                requestedReview.setRepositoryName(request.getRepositoryName());
                requestedReview.setBranchName(request.getBranchName());
                requestedReview.setPullRequestId(request.getPullRequestId());
                return requestedReview;
            });

            Review savedReview = reviewService.saveReview(review, user);

            return ReviewTiming.measure("review_controller_response_mapping", () -> {
                ReviewResponse response = new ReviewResponse();
                response.setId(savedReview.getId());
                response.setRepositoryName(savedReview.getRepositoryName());
                response.setBranchName(savedReview.getBranchName());
                response.setPullRequestId(savedReview.getPullRequestId());
                response.setReviewText(savedReview.getReviewText());

                return response;
            });
        } finally {
            ReviewTiming.restoreCorrelationId(previousCorrelationId);
        }
    }

    @GetMapping("/reviews")
    public List<Review> getAllReviews(@AuthenticationPrincipal User user) {
        return reviewService.getReviewsFor(user);
    }
}