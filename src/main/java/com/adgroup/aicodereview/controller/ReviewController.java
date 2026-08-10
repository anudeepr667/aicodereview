package com.adgroup.aicodereview.controller;
import jakarta.validation.Valid;
import com.adgroup.aicodereview.dto.ReviewRequest;
import com.adgroup.aicodereview.dto.ReviewResponse;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/review")
    public Review getReview() {
        return reviewService.saveSampleReview();
    }

   @PostMapping("/review")
public ReviewResponse createReview(@Valid @RequestBody ReviewRequest request) {

        Review review = new Review();
        review.setRepositoryName(request.getRepositoryName());
        review.setBranchName(request.getBranchName());
        review.setPullRequestId(request.getPullRequestId());

        Review savedReview = reviewService.saveReview(review);

        ReviewResponse response = new ReviewResponse();
        response.setId(savedReview.getId());
        response.setRepositoryName(savedReview.getRepositoryName());
        response.setBranchName(savedReview.getBranchName());
        response.setPullRequestId(savedReview.getPullRequestId());
        response.setReviewText(savedReview.getReviewText());

        return response;
    }

    @GetMapping("/reviews")
    public List<Review> getAllReviews() {
        return reviewService.getAllReviews();
    }
}