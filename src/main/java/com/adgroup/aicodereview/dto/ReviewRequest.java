package com.adgroup.aicodereview.dto;

public class ReviewRequest {

    private String owner;
    private String repositoryName;
    private String branchName;
    private String pullRequestId;

    public ReviewRequest() {
    }

    public String getOwner() {
    return owner;
}

public void setOwner(String owner) {
    this.owner = owner;
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