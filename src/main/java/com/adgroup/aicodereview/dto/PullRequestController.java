package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.dto.PullRequestAnalyzeRequest;
import com.adgroup.aicodereview.dto.PullRequestAnalyzeResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/pull-requests")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
public class PullRequestController {

    private static final Pattern PR_URL_PATTERN =
            Pattern.compile(
                    "^https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)/?$"
            );

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzePullRequest(
            @RequestBody PullRequestAnalyzeRequest request
    ) {

        String url = request.getPullRequestUrl();

        if (url == null || url.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body("Pull Request URL is required.");
        }

        Matcher matcher =
                PR_URL_PATTERN.matcher(url.trim());

        if (!matcher.matches()) {
            return ResponseEntity
                    .badRequest()
                    .body("Invalid GitHub Pull Request URL.");
        }

        String owner = matcher.group(1);
        String repository = matcher.group(2);
        int pullRequestNumber =
                Integer.parseInt(matcher.group(3));

        PullRequestAnalyzeResponse response =
                new PullRequestAnalyzeResponse(
                        owner,
                        repository,
                        pullRequestNumber
                );

        return ResponseEntity.ok(response);
    }
}