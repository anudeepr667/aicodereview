package com.adgroup.aicodereview.github;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GitHubService {

    private final WebClient webClient = WebClient.create();

    public String getRepositoryDetails() {

        return webClient
                .get()
                .uri("https://api.github.com/repos/spring-projects/spring-boot")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}