package com.adgroup.aicodereview.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

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

    @Column(updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String languages;

    @Column(columnDefinition = "TEXT")
    private String technologies;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Review() {
}

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

public Instant getCreatedAt() {
    return createdAt;
}

public String getLanguages() {
    return languages;
}

public void setLanguages(String languages) {
    this.languages = languages;
}

public String getTechnologies() {
    return technologies;
}

public void setTechnologies(String technologies) {
    this.technologies = technologies;
}

@JsonIgnore
public User getUser() {
    return user;
}

public void setUser(User user) {
    this.user = user;
}



}