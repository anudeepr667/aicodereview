package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.dto.ReviewRequest;
import com.adgroup.aicodereview.dto.ReviewResponse;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.service.ReviewService;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
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
    public ReviewResponse createReview(@RequestBody ReviewRequest request) {

        Review review = new Review();

        review.setOwner(request.getOwner());
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