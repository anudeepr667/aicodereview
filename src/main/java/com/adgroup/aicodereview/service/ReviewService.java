package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.github.GitHubService;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final GitHubService gitHubService;
    private final AIService aiService;

    public ReviewService(
            ReviewRepository reviewRepository,
            GitHubService gitHubService,
            AIService aiService) {

        this.reviewRepository = reviewRepository;
        this.gitHubService = gitHubService;
        this.aiService = aiService;
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

        // Example repositoryName:
        // spring-projects/spring-boot

        String[] repositoryParts = review.getRepositoryName().split("/");

        if (repositoryParts.length != 2) {
            throw new IllegalArgumentException(
                    "repositoryName must be in the format owner/repository");
        }

        String owner = repositoryParts[0];
        String repo = repositoryParts[1];

        // Get the real Pull Request diff from GitHub
        String diff = gitHubService.getPullRequestDiff(
                owner,
                repo,
                review.getPullRequestId()
        );

        // Send the real diff to Gemini
        String reviewText = aiService.reviewCode(diff);

        // Store Gemini's review in PostgreSQL
        review.setReviewText(reviewText);

        return reviewRepository.save(review);
    }

    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }
}