package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import java.util.List;


@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public Review saveSampleReview() {

        Review review = new Review();

        review.setRepositoryName("AI-Code-Review");
        review.setBranchName("feature/login");
        review.setPullRequestId("25");
        review.setReviewText("Looks good. Consider using BCrypt.");

        return reviewRepository.save(review);
    }

    public Review saveReview(Review review) {
        return reviewRepository.save(review);
    }
    public List<Review> getAllReviews() {
    return reviewRepository.findAll();
}
}