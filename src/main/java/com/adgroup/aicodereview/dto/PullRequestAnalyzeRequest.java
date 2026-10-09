package com.adgroup.aicodereview.dto;

public class PullRequestAnalyzeRequest {

    private String pullRequestUrl;

    public PullRequestAnalyzeRequest() {
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public void setPullRequestUrl(String pullRequestUrl) {
        this.pullRequestUrl = pullRequestUrl;
    }
}