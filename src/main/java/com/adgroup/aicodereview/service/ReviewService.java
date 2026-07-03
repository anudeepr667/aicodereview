package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.model.Review;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    public Review getSampleReview() {

        Review review = new Review();

        review.setId(1L);
        review.setRepositoryName("AI-Code-Review");
        review.setBranchName("feature/login");
        review.setPullRequestId("25");
        review.setReviewText("Looks good. Consider using BCrypt for password hashing.");

        return review;
    }
}