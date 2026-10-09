
package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.dto.AIReviewResult;
import com.adgroup.aicodereview.github.GitHubService;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.repository.ReviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final GitHubService gitHubService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public ReviewService(
            ReviewRepository reviewRepository,
            GitHubService gitHubService,
            AIService aiService,
            ObjectMapper objectMapper) {

        this.reviewRepository = reviewRepository;
        this.gitHubService = gitHubService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
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

        String[] repositoryParts = review.getRepositoryName().split("/");

        if (repositoryParts.length != 2) {
            throw new IllegalArgumentException(
                    "repositoryName must be in the format owner/repository");
        }

        String owner = repositoryParts[0];
        String repo = repositoryParts[1];

        String diff = gitHubService.getPullRequestDiff(
                owner,
                repo,
                review.getPullRequestId()
        );

        AIReviewResult aiReviewResult = aiService.reviewCode(diff);

        try {
            String reviewText = objectMapper.writeValueAsString(aiReviewResult);
            review.setReviewText(reviewText);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Unable to serialize the AI review result.", e);
        }

        return reviewRepository.save(review);
    }

    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByIdDesc();
    }
}
