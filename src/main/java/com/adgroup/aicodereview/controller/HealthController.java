package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ReviewService reviewService;

    public HealthController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/review")
    public Review getReview() {
        return reviewService.getSampleReview();
    }
}