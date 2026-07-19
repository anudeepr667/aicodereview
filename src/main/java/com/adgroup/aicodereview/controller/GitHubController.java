package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.github.GitHubService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/github/repository")
    public String getRepository() {
        return gitHubService.getRepositoryDetails();
    }
}