package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.dto.PullRequestAnalyzeRequest;
import com.adgroup.aicodereview.dto.PullRequestAnalyzeResponse;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.model.User;
import com.adgroup.aicodereview.service.ReviewService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/pull-requests")
public class PullRequestController {

    private static final Pattern PR_URL_PATTERN =
            Pattern.compile(
                    "^https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)/?$"
            );

    private final ReviewService reviewService;

    public PullRequestController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzePullRequest(
            @RequestBody PullRequestAnalyzeRequest request
            , @AuthenticationPrincipal User user
    ) {

        String url = request.getPullRequestUrl();

        if (url == null || url.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body("Pull Request URL is required.");
        }

        Matcher matcher = PR_URL_PATTERN.matcher(url.trim());

        if (!matcher.matches()) {
            return ResponseEntity
                    .badRequest()
                    .body("Invalid GitHub Pull Request URL.");
        }

        String owner = matcher.group(1);
        String repository = matcher.group(2);
        String pullRequestNumber = matcher.group(3);

        Review review = new Review();

        review.setOwner(owner);
        review.setRepositoryName(owner + "/" + repository);
        review.setPullRequestId(pullRequestNumber);

        Review savedReview = reviewService.saveReview(review, user);

        return ResponseEntity.ok(savedReview);
    }
}