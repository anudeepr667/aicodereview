
package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.ai.AIService;
import com.adgroup.aicodereview.dto.AIReviewResult;
import com.adgroup.aicodereview.dto.ReviewFinding;
import com.adgroup.aicodereview.github.GitHubService;
import com.adgroup.aicodereview.model.Review;
import com.adgroup.aicodereview.model.Role;
import com.adgroup.aicodereview.model.User;
import com.adgroup.aicodereview.repository.ReviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);
    private static final Pattern DIFF_FILE_PATTERN = Pattern.compile("^diff --git a/(\\S+) b/(\\S+)$", Pattern.MULTILINE);
    private static final int MAX_FINDING_LINE_DISTANCE = 2;
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
    private final SemgrepAnalysisService semgrepAnalysisService;
    private final int maxFindingComparisons;
    private final Executor reviewAnalysisExecutor;

    @Autowired
    public ReviewService(
            ReviewRepository reviewRepository,
            GitHubService gitHubService,
            AIService aiService,
            ObjectMapper objectMapper,
            SemgrepAnalysisService semgrepAnalysisService,
            @Value("${review.finding-deduplication.max-comparisons:5}") int maxFindingComparisons,
            @Qualifier("reviewAnalysisTaskExecutor") TaskExecutor reviewAnalysisExecutor) {
        this(
                reviewRepository,
                gitHubService,
                aiService,
                objectMapper,
                semgrepAnalysisService,
                maxFindingComparisons,
                (Executor) reviewAnalysisExecutor);
    }

    public ReviewService(
            ReviewRepository reviewRepository,
            GitHubService gitHubService,
            AIService aiService,
            ObjectMapper objectMapper,
            SemgrepAnalysisService semgrepAnalysisService,
            int maxFindingComparisons) {
        this(
                reviewRepository,
                gitHubService,
                aiService,
                objectMapper,
                semgrepAnalysisService,
                maxFindingComparisons,
                ForkJoinPool.commonPool());
    }

    private ReviewService(
            ReviewRepository reviewRepository,
            GitHubService gitHubService,
            AIService aiService,
            ObjectMapper objectMapper,
            SemgrepAnalysisService semgrepAnalysisService,
            int maxFindingComparisons,
            Executor reviewAnalysisExecutor) {
        if (maxFindingComparisons < 0) {
            throw new IllegalArgumentException(
                    "Finding deduplication comparison limit cannot be negative.");
        }

        this.reviewRepository = reviewRepository;
        this.gitHubService = gitHubService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.semgrepAnalysisService = semgrepAnalysisService;
        this.maxFindingComparisons = maxFindingComparisons;
        this.reviewAnalysisExecutor = reviewAnalysisExecutor;
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
        return saveReview(review, user, null, null);
    }

    public Review saveWebhookReview(
            Review review,
            String expectedHeadSha,
            String deliveryId) {
        if (expectedHeadSha == null || expectedHeadSha.isBlank()
                || deliveryId == null || deliveryId.isBlank()) {
            throw new IllegalArgumentException("Webhook review requires a head SHA and delivery ID.");
        }
        Optional<Review> existingReview = reviewRepository.findByWebhookDeliveryId(deliveryId);
        if (existingReview.isPresent()) {
            return existingReview.get();
        }
        review.setWebhookDeliveryId(deliveryId);
        return saveReview(review, null, expectedHeadSha, deliveryId);
    }

    private Review saveReview(
            Review review,
            User user,
            String expectedHeadSha,
            String deliveryId) {
        String correlationId = review.getId() == null
                ? ReviewTiming.correlationId()
                : "review-" + review.getId();
        String previousCorrelationId = ReviewTiming.setCorrelationId(correlationId);
        try {
            return ReviewTiming.measure(
                    "review_total",
                    () -> processReview(review, user, expectedHeadSha, deliveryId));
        } finally {
            ReviewTiming.restoreCorrelationId(previousCorrelationId);
        }
    }

    private Review processReview(
            Review review,
            User user,
            String expectedHeadSha,
            String deliveryId) {
        review.setUser(user);

        String[] repositoryParts = review.getRepositoryName().split("/");

        if (repositoryParts.length != 2) {
            throw new IllegalArgumentException(
                    "repositoryName must be in the format owner/repository");
        }

        String owner = repositoryParts[0];
        String repo = repositoryParts[1];

        GitHubService.PullRequestSnapshot snapshot = gitHubService.getPullRequestSnapshot(
                owner, repo, review.getPullRequestId(), expectedHeadSha);
        String diff = snapshot.diff();

        try {
            review.setLanguages(objectMapper.writeValueAsString(detectLanguages(diff)));
            review.setTechnologies(objectMapper.writeValueAsString(detectTechnologies(diff)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize review stack metadata.", e);
        }

        List<GitHubService.SourceFile> sourceFiles = snapshot.changedFiles().stream()
                .filter(file -> file.newSupported()
                        && file.newContent() != null
                        && file.changes().newPath() != null)
                .map(file -> new GitHubService.SourceFile(
                        file.changes().newPath(), file.newContent()))
                .toList();
        CompletableFuture<List<ReviewFinding>> semgrepFuture =
                ReviewTiming.supplyAsync(
                        reviewAnalysisExecutor,
                        "semgrep_analysis_pipeline",
                        () -> analyzeSourceFiles(sourceFiles));
        CompletableFuture<AIReviewResult> firstPassFuture =
                ReviewTiming.supplyAsync(
                        reviewAnalysisExecutor,
                        "gemini_first_pass",
                        () -> aiService.reviewCode(diff));
        awaitReviewStages(semgrepFuture, firstPassFuture);

        List<ReviewFinding> semgrepFindings = semgrepFuture.join();
        AIReviewResult aiReviewResult = firstPassFuture.join();
        


if (aiReviewResult == null) {
    throw new IllegalStateException("AI review did not return a result.");
}

// First pass: findings from the original review.
List<ReviewFinding> firstPassFindings = aiReviewResult.getFindings();

// Second pass: search independently for additional defects.
List<ReviewFinding> secondPassFindings = List.of();

try {
    secondPassFindings = aiService.reviewAdditionalFindings(
            diff, firstPassFindings);

    LOGGER.info("Gemini second-pass findings: {}",
            secondPassFindings.size());

} catch (RuntimeException e) {
    LOGGER.warn(
            "Second-pass AI review failed; continuing with first-pass findings.",
            e);
}
if (firstPassFindings != null) {
    for (ReviewFinding finding : firstPassFindings) {
        if (finding != null) {
            LOGGER.info(
                    "FIRST PASS finding: file={}, line={}, problem={}",
                    finding.getFile(),
                    finding.getLine(),
                    finding.getProblem());
        }
    }
}

if (secondPassFindings != null) {
    for (ReviewFinding finding : secondPassFindings) {
        if (finding != null) {
            LOGGER.info(
                    "SECOND PASS finding: file={}, line={}, problem={}",
                    finding.getFile(),
                    finding.getLine(),
                    finding.getProblem());
        }
    }
}
// Combine findings from both Gemini passes.
List<ReviewFinding> allGeminiFindings = new ArrayList<>();

if (firstPassFindings != null) {
    allGeminiFindings.addAll(firstPassFindings);
}

if (secondPassFindings != null) {
    allGeminiFindings.addAll(secondPassFindings);
}

LOGGER.info("Gemini first-pass findings: {}",
        firstPassFindings == null ? 0 : firstPassFindings.size());

LOGGER.info("Gemini findings before validation: {}",
        allGeminiFindings.size());

LOGGER.info("Semgrep findings before validation: {}",
        semgrepFindings == null ? 0 : semgrepFindings.size());

ValidationPair validation = ReviewTiming.measure(
        "finding_validation",
        () -> new ValidationPair(
                PullRequestFindingValidator.validate(allGeminiFindings, snapshot),
                PullRequestFindingValidator.validate(semgrepFindings, snapshot)));
PullRequestFindingValidator.ValidationResult geminiValidation = validation.gemini();
PullRequestFindingValidator.ValidationResult semgrepValidation = validation.semgrep();

LOGGER.info("Gemini findings after validation: {}",
        geminiValidation.findings().size());

LOGGER.info("Semgrep findings after validation: {}",
        semgrepValidation.findings().size());

LOGGER.info("Gemini findings omitted: {}",
        geminiValidation.omittedCount());

LOGGER.info("Semgrep findings omitted: {}",
        semgrepValidation.omittedCount());

        int omittedFindingCount =
                geminiValidation.omittedCount() + semgrepValidation.omittedCount();
        if (omittedFindingCount > 0) {
            LOGGER.warn("{} finding(s) could not be verified against changed pull request lines and were omitted.",
                    omittedFindingCount);
            aiReviewResult.setSummary(appendValidationNote(
                    aiReviewResult.getSummary(), omittedFindingCount));
        }
        List<ReviewFinding> combinedFindings = ReviewTiming.measure(
                "finding_deduplication_and_merging",
                () -> combineFindings(
                        geminiValidation.findings(),
                        semgrepValidation.findings(),
                        aiService,
                        maxFindingComparisons));
        aiReviewResult.setFindings(combinedFindings);
        aiReviewResult.setOverallScore(ReviewTiming.measure(
                "score_calculation", () -> calculateScore(combinedFindings)));

        String reviewText = ReviewTiming.measure(
                "review_result_serialization",
                () -> {
                    try {
                        return objectMapper.writeValueAsString(aiReviewResult);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(
                                "Unable to serialize the AI review result.", e);
                    }
                });
        review.setReviewText(reviewText);

        return ReviewTiming.measure("database_persistence", () -> {
            try {
                return reviewRepository.save(review);
            } catch (RuntimeException e) {
                if (deliveryId != null) {
                    Optional<Review> savedWebhookReview =
                            reviewRepository.findByWebhookDeliveryId(deliveryId);
                    if (savedWebhookReview.isPresent()) {
                        return savedWebhookReview.get();
                    }
                }
                throw e;
            }
        });
    }

    private record ValidationPair(
            PullRequestFindingValidator.ValidationResult gemini,
            PullRequestFindingValidator.ValidationResult semgrep) {
    }

    private static void awaitReviewStages(CompletableFuture<?>... stages) {
        try {
            CompletableFuture.allOf(stages).join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("A review analysis stage failed.", cause);
        }
    }

    private static String appendValidationNote(String summary, int omittedFindingCount) {
        String note = omittedFindingCount
                + " finding(s) were omitted because their file or line could not be verified "
                + "against the changed pull request snapshot.";
        return summary == null || summary.isBlank() ? note : summary.trim() + " " + note;
    }

    private List<ReviewFinding> analyzeSourceFiles(List<GitHubService.SourceFile> sourceFiles) {
        if (sourceFiles == null) {
            throw new IllegalStateException("GitHub did not return changed source files.");
        }

        Path workspace;
        try {
            workspace = Files.createTempDirectory("aicodereview-semgrep-");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the Semgrep source workspace.");
        }

        RuntimeException analysisFailure = null;
        try {
            Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
            ReviewTiming.measureChecked("semgrep_source_preparation", () -> {
                for (GitHubService.SourceFile sourceFile : sourceFiles) {
                    if (sourceFile == null || sourceFile.content() == null) {
                        throw new IllegalStateException(
                                "GitHub returned incomplete changed source file content.");
                    }
                    GitHubService.validateRepositoryPath(sourceFile.path());
                    Path target = normalizedWorkspace.resolve(sourceFile.path()).normalize();
                    if (!target.startsWith(normalizedWorkspace)) {
                        throw new IllegalStateException(
                                "GitHub returned an unsafe changed-file path.");
                    }
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, sourceFile.content(), StandardCharsets.UTF_8);
                }
                return null;
            });
            return semgrepAnalysisService.analyze(normalizedWorkspace);
        } catch (IOException e) {
            analysisFailure = new IllegalStateException(
                    "Unable to prepare changed source files for Semgrep.");
            throw analysisFailure;
        } catch (RuntimeException e) {
            analysisFailure = e;
            throw e;
        } finally {
            try {
                deleteWorkspace(workspace);
            } catch (IllegalStateException cleanupFailure) {
                if (analysisFailure == null) {
                    throw cleanupFailure;
                }
                analysisFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    static void deleteWorkspace(Path workspace) {
        List<IOException> failures = new ArrayList<>();
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException | UncheckedIOException e) {
                            if (e instanceof UncheckedIOException uncheckedIOException) {
                                failures.add(uncheckedIOException.getCause());
                            } else {
                                failures.add((IOException) e);
                            }
                        }
                    });
        } catch (IOException e) {
            failures.add(e);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Unable to clean up the Semgrep source workspace.");
        }
    }

    
static List<ReviewFinding> combineFindings(
        List<ReviewFinding> geminiFindings,
        List<ReviewFinding> semgrepFindings,
        AIService aiService,
        int maxComparisons) {

    Map<FindingIdentity, ReviewFinding> exactFindings = new HashMap<>();
    List<ReviewFinding> combined = new ArrayList<>();
    List<ReviewFinding> retainedGemini = new ArrayList<>();

    // First, merge exact duplicates from both Gemini passes.
    addFindings(combined, retainedGemini, exactFindings, geminiFindings);

    Set<ReviewFinding> duplicates =
            Collections.newSetFromMap(new IdentityHashMap<>());

    int comparisons = 0;

    // Then compare potentially duplicate Gemini findings semantically.
    geminiDeduplication:
    for (int laterIndex = 0;
            laterIndex < retainedGemini.size();
            laterIndex++) {

        ReviewFinding later = retainedGemini.get(laterIndex);

        if (duplicates.contains(later)) {
            continue;
        }

        for (int earlierIndex = 0;
                earlierIndex < laterIndex;
                earlierIndex++) {

            ReviewFinding earlier = retainedGemini.get(earlierIndex);

            if (duplicates.contains(earlier)) {
                continue;
            }

            if (!isPlausibleDuplicateCandidate(earlier, later)) {
                continue;
            }

            if (comparisons >= maxComparisons) {
                LOGGER.debug(
                        "Semantic finding comparison limit reached at {} comparisons.",
                        comparisons);
                break geminiDeduplication;
            }

            comparisons++;

            try {
                if (aiService.areDuplicateFindings(earlier, later)) {

                    // Preserve both findings' useful details in one finding.
                    mergeExactFinding(earlier, later);

                    // Redirect the duplicate's exact identity to the
                    // retained finding so later Semgrep results merge correctly.
                    FindingIdentity duplicateIdentity = new FindingIdentity(
                            normalizeFindingPath(later.getFile()),
                            later.getLine(),
                            normalizeFindingText(later.getProblem()));

                    exactFindings.put(duplicateIdentity, earlier);
                    duplicates.add(later);

                    LOGGER.info(
                            "Merged duplicate Gemini findings: file={}, lines={}/{}",
                            earlier.getFile(),
                            earlier.getLine(),
                            later.getLine());

                    break;
                }
            } catch (RuntimeException e) {
                LOGGER.warn(
                        "Gemini finding comparison failed; retaining both findings.",
                        e);
            }
        }
    }

    // Remove semantic duplicates before adding Semgrep results.
    combined.removeIf(duplicates::contains);
    retainedGemini.removeIf(duplicates::contains);

    // Add Semgrep findings, merging exact matches with retained findings.
    int semgrepStart = combined.size();

    addFindings(
            combined,
            new ArrayList<>(),
            exactFindings,
            semgrepFindings);

    // Compare remaining Semgrep findings against retained Gemini findings.
    for (int semgrepIndex = semgrepStart;
            semgrepIndex < combined.size();
            semgrepIndex++) {

        ReviewFinding semgrepFinding = combined.get(semgrepIndex);

        for (ReviewFinding geminiFinding : retainedGemini) {

            if (!isPlausibleDuplicateCandidate(
                    geminiFinding, semgrepFinding)) {
                continue;
            }

            if (comparisons >= maxComparisons) {
                break;
            }

            comparisons++;

            try {
                if (aiService.areDuplicateFindings(
                        geminiFinding, semgrepFinding)) {

                    mergeExactFinding(geminiFinding, semgrepFinding);
                    duplicates.add(semgrepFinding);
                    break;
                }
            } catch (RuntimeException e) {
                LOGGER.warn(
                        "AI finding comparison failed; retaining both findings.",
                        e);
            }
        }
    }

    combined.removeIf(duplicates::contains);

    combined.sort(Comparator
            .comparing(
                    (ReviewFinding finding) ->
                            normalizeFindingPath(finding.getFile()),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                    ReviewFinding::getLine,
                    Comparator.nullsLast(Integer::compareTo))
            .thenComparing(
                    finding -> normalizeFindingText(finding.getProblem()))
            .thenComparing(
                    finding -> normalizeFindingText(finding.getCategory())));

    return combined;
}


    private static void addFindings(
            List<ReviewFinding> combined,
            List<ReviewFinding> retainedSourceFindings,
            Map<FindingIdentity, ReviewFinding> seen,
            List<ReviewFinding> findings) {
        if (findings == null) {
            return;
        }
        for (ReviewFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            finding.setSeverity(normalizeSeverity(finding.getSeverity()));
            FindingIdentity identity = new FindingIdentity(
                    normalizeFindingPath(finding.getFile()),
                    finding.getLine(),
                    normalizeFindingText(finding.getProblem()));
            if (identity.file() == null
                    || identity.file().isBlank()
                    || identity.line() == null
                    || identity.line() <= 0
                    || identity.problem() == null
                    || identity.problem().isBlank()) {
                combined.add(finding);
                retainedSourceFindings.add(finding);
                continue;
            }

            ReviewFinding existing = seen.putIfAbsent(identity, finding);
            if (existing == null) {
                combined.add(finding);
                retainedSourceFindings.add(finding);
            } else {
                mergeExactFinding(existing, finding);
            }
        }
    }

    private static void mergeExactFinding(ReviewFinding retained, ReviewFinding duplicate) {
        String retainedSeverity = normalizeSeverity(retained.getSeverity());
        String duplicateSeverity = normalizeSeverity(duplicate.getSeverity());
        retained.setSeverity(severityPenalty(retainedSeverity) >= severityPenalty(duplicateSeverity)
                ? retainedSeverity
                : duplicateSeverity);
        retained.setCategory(preserveExisting(retained.getCategory(), duplicate.getCategory()));
        retained.setFile(preserveExisting(retained.getFile(), duplicate.getFile()));
        retained.setProblem(preserveExisting(retained.getProblem(), duplicate.getProblem()));
        retained.setExplanation(mergeText(retained.getExplanation(), duplicate.getExplanation()));
        retained.setSuggestedFix(mergeText(retained.getSuggestedFix(), duplicate.getSuggestedFix()));
        if (retained.getLine() == null) {
            retained.setLine(duplicate.getLine());
        }
    }

    private static String mergeText(String existing, String incoming) {
        if (existing == null || existing.isBlank()) {
            return incoming;
        }
        if (incoming == null || incoming.isBlank()
                || normalizeFindingText(existing).equals(normalizeFindingText(incoming))) {
            return existing;
        }
        return existing.trim() + "\n\n" + incoming.trim();
    }

    private static String preserveExisting(String existing, String incoming) {
        return existing == null || existing.isBlank() ? incoming : existing;
    }

    private static String normalizeFindingPath(String file) {
        if (file == null) {
            return null;
        }
        try {
            return Path.of(file.replace('\\', '/'))
                    .normalize()
                    .toString()
                    .replace('\\', '/');
        } catch (InvalidPathException e) {
            return file.replace('\\', '/');
        }
    }

    private static String normalizeFindingText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static double calculateScore(List<ReviewFinding> findings) {
        Map<FindingIdentity, Integer> penaltiesByFinding = new HashMap<>();
        long incompleteIdentityPenalty = 0;
        if (findings != null) {
            for (ReviewFinding finding : findings) {
                if (finding == null) {
                    continue;
                }

                finding.setSeverity(normalizeSeverity(finding.getSeverity()));
                FindingIdentity identity = completeFindingIdentity(finding);
                if (identity == null) {
                    incompleteIdentityPenalty += severityPenalty(finding.getSeverity());
                    continue;
                }
                penaltiesByFinding.merge(identity, severityPenalty(finding.getSeverity()), Math::max);
            }
        }
        long totalPenalty = penaltiesByFinding.values().stream()
                .mapToLong(Integer::longValue)
                .sum() + incompleteIdentityPenalty;
        return Math.max(0L, 100L - totalPenalty);
    }

    private static FindingIdentity completeFindingIdentity(ReviewFinding finding) {
        String file = finding.getFile();
        String problem = finding.getProblem();
        Integer line = finding.getLine();
        if (file == null || file.isBlank()
                || line == null || line <= 0
                || problem == null || problem.isBlank()) {
            return null;
        }

        String normalizedFile = normalizeFindingPath(file);
        String normalizedProblem = normalizeFindingText(problem);
        if (normalizedFile == null || normalizedFile.isBlank() || normalizedProblem.isBlank()) {
            return null;
        }
        return new FindingIdentity(normalizedFile, line, normalizedProblem);
    }

    private static String normalizeSeverity(String severity) {
        if (severity == null) {
            return "Medium";
        }
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "Critical";
            case "HIGH", "ERROR" -> "High";
            case "MEDIUM", "WARNING" -> "Medium";
            case "LOW", "INFO" -> "Low";
            default -> "Medium";
        };
    }

    private static int severityPenalty(String severity) {
        return switch (severity) {
            case "Critical" -> 40;
            case "High" -> 20;
            case "Low" -> 2;
            default -> 8;
        };
    }

    private static boolean isPlausibleDuplicateCandidate(
            ReviewFinding geminiFinding,
            ReviewFinding semgrepFinding) {
        String geminiPath = normalizeFindingPath(geminiFinding.getFile());
        String semgrepPath = normalizeFindingPath(semgrepFinding.getFile());
        Integer geminiLine = geminiFinding.getLine();
        Integer semgrepLine = semgrepFinding.getLine();
        if (geminiPath == null || !geminiPath.equals(semgrepPath)
                || geminiLine == null || semgrepLine == null
                || geminiLine <= 0 || semgrepLine <= 0
                || Math.abs((long) geminiLine - semgrepLine) > MAX_FINDING_LINE_DISTANCE) {
            return false;
        }

        Set<String> geminiDomains = categoryDomains(geminiFinding.getCategory());
        Set<String> semgrepDomains = categoryDomains(semgrepFinding.getCategory());
        return geminiDomains.isEmpty() || semgrepDomains.isEmpty()
                || !Collections.disjoint(geminiDomains, semgrepDomains);
    }

    private static Set<String> categoryDomains(String category) {
        String normalized = normalizeFindingText(category);
        Map<String, List<String>> indicators = Map.of(
                "security", List.of("security", "sql", "injection", "xss", "auth", "crypto", "secret", "permission"),
                "performance", List.of("performance", "complexity", "inefficient", "slow"),
                "correctness", List.of("correctness", "bug", "logic", "null", "type"),
                "reliability", List.of("reliability", "availability", "exception", "error", "failure"),
                "style", List.of("style", "format", "naming", "convention"),
                "maintainability", List.of("maintainability", "maintainable", "readability", "duplication"),
                "concurrency", List.of("concurrency", "race", "thread", "lock"),
                "resource", List.of("resource", "memory", "leak", "close"));
        Set<String> domains = new HashSet<>();
        for (Map.Entry<String, List<String>> indicator : indicators.entrySet()) {
            if (indicator.getValue().stream().anyMatch(normalized::contains)) {
                domains.add(indicator.getKey());
            }
        }
        return domains;
    }

    private record FindingIdentity(String file, Integer line, String problem) {
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
