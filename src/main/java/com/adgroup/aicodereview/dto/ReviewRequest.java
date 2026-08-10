package com.adgroup.aicodereview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReviewRequest {

    @NotBlank(message = "Repository name is required")
    @Size(max = 100, message = "Repository name must not exceed 100 characters")
    private String repositoryName;

    @NotBlank(message = "Branch name is required")
    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    private String branchName;

    @NotBlank(message = "Pull request ID is required")
    @Size(max = 50, message = "Pull request ID must not exceed 50 characters")
    private String pullRequestId;

    public ReviewRequest() {
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getPullRequestId() {
        return pullRequestId;
    }

    public void setPullRequestId(String pullRequestId) {
        this.pullRequestId = pullRequestId;
    }
}