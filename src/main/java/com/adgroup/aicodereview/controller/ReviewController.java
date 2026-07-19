package com.adgroup.aicodereview.controller;

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
    public Review createReview(@RequestBody Review review) {
        return reviewService.saveReview(review);
    }

    @GetMapping("/reviews")
    public List<Review> getAllReviews() {
        return reviewService.getAllReviews();
    }
}