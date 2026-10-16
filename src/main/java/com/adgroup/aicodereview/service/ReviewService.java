
package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.dto.AIReviewResult;
import com.adgroup.aicodereview.github.GitHubService;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.model.Role;
import com.adgroup.aicodereview.model.User;
import com.adgroup.aicodereview.repository.ReviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewService {

    private static final Pattern DIFF_FILE_PATTERN = Pattern.compile("^diff --git a/(\\S+) b/(\\S+)$", Pattern.MULTILINE);
    private static final Map<String, String> LANGUAGE_BY_EXTENSION = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("py", "Python"),
            Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("go", "Go"), Map.entry("cs", "C#"),
            Map.entry("cpp", "C++"), Map.entry("cc", "C++"), Map.entry("c", "C"),
            Map.entry("rb", "Ruby"), Map.entry("php", "PHP"), Map.entry("rs", "Rust"),
            Map.entry("swift", "Swift"), Map.entry("sql", "SQL"), Map.entry("html", "HTML"),
            Map.entry("css", "CSS"), Map.entry("scss", "SCSS"), Map.entry("xml", "XML"),
            Map.entry("yaml", "YAML"), Map.entry("yml", "YAML"), Map.entry("json", "JSON")
    );
            private static final Pattern DIFF_PATH_PATTERN = Pattern.compile("^diff --git a/(\\S+) b/(\\S+)$", Pattern.MULTILINE);

    private final ReviewRepository reviewRepository;
    private final GitHubService gitHubService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public ReviewService(
            ReviewRepository reviewRepository,
            GitHubService gitHubService,
            AIService aiService,
            ObjectMapper objectMapper) {

        this.reviewRepository = reviewRepository;
        this.gitHubService = gitHubService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    public Review saveSampleReview(User user) {

        Review review = new Review();

        review.setUser(user);

        review.setRepositoryName("AI-Code-Review");
        review.setBranchName("feature/login");
        review.setPullRequestId("25");
        review.setReviewText("Looks good. Consider using BCrypt.");

        return reviewRepository.save(review);
    }

    public Review saveReview(Review review, User user) {
        review.setUser(user);

        String[] repositoryParts = review.getRepositoryName().split("/");

        if (repositoryParts.length != 2) {
            throw new IllegalArgumentException(
                    "repositoryName must be in the format owner/repository");
        }

        String owner = repositoryParts[0];
        String repo = repositoryParts[1];

        String diff = gitHubService.getPullRequestDiff(
                owner,
                repo,
                review.getPullRequestId()
        );

        try {
            review.setLanguages(objectMapper.writeValueAsString(detectLanguages(diff)));
            review.setTechnologies(objectMapper.writeValueAsString(detectTechnologies(diff)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize review stack metadata.", e);
        }

        AIReviewResult aiReviewResult = aiService.reviewCode(diff);

        try {
            String reviewText = objectMapper.writeValueAsString(aiReviewResult);
            review.setReviewText(reviewText);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Unable to serialize the AI review result.", e);
        }

        return reviewRepository.save(review);
    }

    static List<String> detectLanguages(String diff) {
        Set<String> languages = new LinkedHashSet<>();
        Matcher matcher = DIFF_FILE_PATTERN.matcher(diff == null ? "" : diff);
        while (matcher.find()) {
            addLanguage(languages, matcher.group(1));
            addLanguage(languages, matcher.group(2));
        }
        return new ArrayList<>(languages);
    }

    static List<String> detectTechnologies(String diff) {
        String content = diff == null ? "" : diff;
        String lowerContent = content.toLowerCase();
        Set<String> technologies = new LinkedHashSet<>();
        Matcher matcher = DIFF_PATH_PATTERN.matcher(content);
        while (matcher.find()) {
            addTechnologyForPath(technologies, matcher.group(1));
            addTechnologyForPath(technologies, matcher.group(2));
        }
        if (lowerContent.contains("spring-boot-starter") || lowerContent.contains("org.springframework.boot")) {
            technologies.add("Spring Boot");
        }
        if (lowerContent.matches("(?s).*\\\"react\\\"\\s*:.*") || lowerContent.contains("@react/")) {
            technologies.add("React");
        }
        if (lowerContent.matches("(?s).*\\\"@angular/core\\\"\\s*:.*")) {
            technologies.add("Angular");
        }
        if (lowerContent.contains("django")) {
            technologies.add("Django");
        }
        return new ArrayList<>(technologies);
    }

    private static void addTechnologyForPath(Set<String> technologies, String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        if (fileName.equals("pom.xml")) technologies.add("Maven");
        if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")) technologies.add("Gradle");
        if (fileName.equals("package.json")) technologies.add("Node.js");
        if (fileName.equals("manage.py")) technologies.add("Django");
        if (fileName.endsWith(".csproj") || fileName.endsWith(".sln")) technologies.add(".NET");
        if (fileName.equals("dockerfile")) technologies.add("Docker");
    }

    private static void addLanguage(Set<String> languages, String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) return;
        String language = LANGUAGE_BY_EXTENSION.get(fileName.substring(extensionStart + 1).toLowerCase());
        if (language != null) languages.add(language);
    }

    public List<Review> getReviewsFor(User user) {
        return user.getRole() == Role.ADMIN
                ? reviewRepository.findAllByOrderByIdDesc()
                : reviewRepository.findByUserOrderByIdDesc(user);
    }
}
