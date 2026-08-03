package com.adgroup.aicodereview.model;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String repositoryName;
    private String branchName;
    private String pullRequestId;
    private String reviewText;

    public Review() {
}

    public Review(Long id,String repositoryName,String branchName,String pullRequestId,String reviewText) {
         this.id=id;
         this.repositoryName=repositoryName;
         this.branchName=branchName;
         this.pullRequestId=pullRequestId;
         this.reviewText=reviewText;
         
    }
    public Long getId() {
    return id;
}

public void setId(Long id) {
    this.id = id;
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

public String getReviewText() {
    return reviewText;
}

public void setReviewText(String reviewText) {
    this.reviewText = reviewText;
}



}