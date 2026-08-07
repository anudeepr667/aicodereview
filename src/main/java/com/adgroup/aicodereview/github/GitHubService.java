package com.adgroup.aicodereview.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GitHubService {

    private final WebClient webClient;

    public GitHubService(
            @Value("${github.api.url}") String apiUrl,
            @Value("${github.token}") String token) {

        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    // Keeps your original endpoint working
    public String getRepositoryDetails() {
        return webClient
                .get()
                .uri("/repos/spring-projects/spring-boot")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getRepository(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getPullRequests(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}/pulls?per_page=5", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getBranches(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}/branches", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getContents(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}/contents", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}