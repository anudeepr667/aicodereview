package com.adgroup.aicodereview.model;
import jakarta.persistence.Column;
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
    private String owner;
    private String repositoryName;
    private String branchName;
    private String pullRequestId;
    @Column(columnDefinition = "TEXT")
private String reviewText;

    public Review() {
}

   public Review(Long id,
              String owner,
              String repositoryName,
              String branchName,
              String pullRequestId,
              String reviewText){
         
    }
    public Long getId() {
    return id;
}

public void setId(Long id) {
    this.id = id;
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

public String getReviewText() {
    return reviewText;
}

public void setReviewText(String reviewText) {
    this.reviewText = reviewText;
}



}