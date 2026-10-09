
package com.adgroup.aicodereview.dto;

import java.util.List;

public class AIReviewResult {

    private double overallScore;
    private String summary;
    private List<CategoryReview> categoryReviews;
    private List<ReviewFinding> findings;
    private List<String> positiveAspects;

    public AIReviewResult() {
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<CategoryReview> getCategoryReviews() {
        return categoryReviews;
    }

    public void setCategoryReviews(List<CategoryReview> categoryReviews) {
        this.categoryReviews = categoryReviews;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<ReviewFinding> findings) {
        this.findings = findings;
    }

    public List<String> getPositiveAspects() {
        return positiveAspects;
    }

    public void setPositiveAspects(List<String> positiveAspects) {
        this.positiveAspects = positiveAspects;
    }
}
