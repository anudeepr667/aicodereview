package com.adgroup.aicodereview.dto;

public class PullRequestAnalyzeResponse {

    private String owner;
    private String repository;
    private int pullRequestNumber;

    public PullRequestAnalyzeResponse(
            String owner,
            String repository,
            int pullRequestNumber
    ) {
        this.owner = owner;
        this.repository = repository;
        this.pullRequestNumber = pullRequestNumber;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public int getPullRequestNumber() {
        return pullRequestNumber;
    }
}