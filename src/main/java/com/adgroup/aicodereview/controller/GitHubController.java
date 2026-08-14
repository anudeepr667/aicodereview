package com.adgroup.aicodereview.controller;

import com.adgroup.aicodereview.github.GitHubService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/repository")
    public String getRepository() {
        return gitHubService.getRepositoryDetails();
    }

    @GetMapping("/repository/{owner}/{repo}")
    public String repository(
            @PathVariable String owner,
            @PathVariable String repo) {

        return gitHubService.getRepository(owner, repo);
    }

    @GetMapping("/pulls/{owner}/{repo}")
    public String pulls(
            @PathVariable String owner,
            @PathVariable String repo) {

        return gitHubService.getPullRequests(owner, repo);
    }

    @GetMapping("/branches/{owner}/{repo}")
    public String branches(
            @PathVariable String owner,
            @PathVariable String repo) {

        return gitHubService.getBranches(owner, repo);
    }

    @GetMapping("/contents/{owner}/{repo}")
    public String contents(
            @PathVariable String owner,
            @PathVariable String repo) {

        return gitHubService.getContents(owner, repo);
    }
    @GetMapping("/diff/{owner}/{repo}/{pull}")
public String diff(
        @PathVariable String owner,
        @PathVariable String repo,
        @PathVariable String pull) {

    return gitHubService.getPullRequestDiff(owner, repo, pull);
}
}