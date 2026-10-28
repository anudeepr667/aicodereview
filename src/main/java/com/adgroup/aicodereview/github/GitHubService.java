package com.adgroup.aicodereview.github;

import com.adgroup.aicodereview.service.ReviewTiming;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GitHubService {

    private static final int FILES_PER_PAGE = 100;
    private static final long MAX_SOURCE_FILE_BYTES = 1_048_576;
    private static final long MAX_TOTAL_SOURCE_BYTES = 10_485_760;
    private static final Set<String> SUPPORTED_SOURCE_EXTENSIONS = Set.of(
            "java", "py", "js", "jsx", "ts", "tsx", "go", "rb", "php",
            "c", "h", "cc", "cpp", "cxx", "hpp", "cs", "kt");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GitHubService(
            @Value("${github.api.url}") String apiUrl,
            @Value("${github.token}") String token,
            ObjectMapper objectMapper) {
        this(WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .build(), objectMapper);
    }

    GitHubService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    // Keeps your original endpoint working
    public String getRepositoryDetails() {
        return webClient
                .get()
                .uri("/repos/spring-projects/spring-boot")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getRepository(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getPullRequests(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}/pulls?per_page=5", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getBranches(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}/branches", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getContents(String owner, String repo) {

        return webClient
                .get()
                .uri("/repos/{owner}/{repo}/contents", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
public String getPullRequestDiff(String owner, String repo, String pullNumber) {

    return webClient
            .get()
            .uri("/repos/{owner}/{repo}/pulls/{pull}",
                    owner, repo, pullNumber)
            .header("Accept", "application/vnd.github.diff")
            .retrieve()
            .bodyToMono(String.class)
            .block();
}

    public String getPullRequestDiff(
            String owner,
            String repo,
            String pullNumber,
            String expectedHeadSha) {
        requirePullRequestHead(owner, repo, pullNumber, expectedHeadSha);
        String diff = getPullRequestDiff(owner, repo, pullNumber);
        requirePullRequestHead(owner, repo, pullNumber, expectedHeadSha);
        return diff;
    }

    public PullRequestSnapshot getPullRequestSnapshot(
            String owner,
            String repo,
            String pullNumber,
            String expectedHeadSha) {
        PullRequestRefs initialRefs = ReviewTiming.measure(
                "github_pr_metadata_retrieval_initial",
                () -> getPullRequestRefs(owner, repo, pullNumber));
        if (expectedHeadSha != null
                && !expectedHeadSha.equalsIgnoreCase(initialRefs.headSha())) {
            throw pullRequestRevisionMismatch();
        }

        String mergeBaseSha = ReviewTiming.measure(
                "github_merge_base_retrieval",
                () -> getCompareMergeBase(
                        owner, repo, initialRefs.baseSha(), initialRefs.headSha()));
        String diff = ReviewTiming.measure(
                "github_diff_retrieval",
                () -> getCompareDiff(owner, repo, initialRefs.baseSha(), initialRefs.headSha()));
        PullRequestDiff parsedDiff = ReviewTiming.measure(
                "github_diff_parsing", () -> PullRequestDiff.parse(diff));
        List<ChangedFileSnapshot> changedFiles = ReviewTiming.measure(
                "github_changed_source_retrieval",
                () -> {
                    List<ChangedFileSnapshot> retrievedFiles = new ArrayList<>();
                    long totalBytes = 0;
                    for (PullRequestDiff.FileChanges file : parsedDiff.files()) {
                        if (file.oldPath() == null && file.newPath() == null) {
                            throw new IllegalStateException(
                                    "GitHub returned a diff without a file path.");
                        }
                        if (file.oldPath() != null) {
                            validateRepositoryPath(file.oldPath());
                        }
                        if (file.newPath() != null) {
                            validateRepositoryPath(file.newPath());
                        }

                        boolean oldSupported =
                                file.oldPath() != null && isSupportedSourceFile(file.oldPath());
                        boolean newSupported =
                                file.newPath() != null && isSupportedSourceFile(file.newPath());
                        String oldContent = null;
                        String newContent = null;
                        if (oldSupported && !file.deletedLines().isEmpty()) {
                            oldContent = getSourceFileContent(
                                    owner, repo, file.oldPath(), mergeBaseSha);
                            totalBytes += oldContent.getBytes(StandardCharsets.UTF_8).length;
                        }
                        if (newSupported) {
                            newContent = getSourceFileContent(
                                    owner, repo, file.newPath(), initialRefs.headSha());
                            totalBytes += newContent.getBytes(StandardCharsets.UTF_8).length;
                        }
                        if (totalBytes > MAX_TOTAL_SOURCE_BYTES) {
                            throw new IllegalStateException(
                                    "Changed source files exceed the 10 MiB total analysis limit.");
                        }
                        retrievedFiles.add(new ChangedFileSnapshot(
                                file, oldContent, newContent, oldSupported, newSupported));
                    }
                    return retrievedFiles;
                });

        PullRequestRefs finalRefs = ReviewTiming.measure(
                "github_pr_metadata_retrieval_final",
                () -> getPullRequestRefs(owner, repo, pullNumber));
        if (!initialRefs.equals(finalRefs)) {
            throw pullRequestRevisionMismatch();
        }
        return new PullRequestSnapshot(
                diff,
                initialRefs.baseSha(),
                initialRefs.headSha(),
                mergeBaseSha,
                List.copyOf(changedFiles));
    }
    public PullRequestContext getPullRequestContext(
        String owner,
        String repo,
        String pullNumber,
        String expectedHeadSha) {

    String response = getJson(
            "/repos/{owner}/{repo}/pulls/{pull}",
            "pull request context",
            owner,
            repo,
            pullNumber);

    try {
        JsonNode pullRequest = objectMapper.readTree(response);

        String actualHeadSha =
                pullRequest.path("head").path("sha").asText("");

        if (expectedHeadSha == null
                || expectedHeadSha.isBlank()
                || actualHeadSha.isBlank()
                || !expectedHeadSha.equalsIgnoreCase(actualHeadSha)) {
            throw pullRequestRevisionMismatch();
        }

        String title = pullRequest.path("title").asText("");
        String body = pullRequest.path("body").asText("");

        return new PullRequestContext(title, body);

    } catch (IOException e) {
        throw new IllegalStateException(
                "GitHub returned invalid pull request context.", e);
    }
}

    private String getCompareMergeBase(
            String owner,
            String repo,
            String baseSha,
            String headSha) {
        String response = getJson(
                "/repos/{owner}/{repo}/compare/{base}...{head}",
                "pull request merge base",
                owner,
                repo,
                baseSha,
                headSha);
        try {
            String mergeBaseSha = objectMapper.readTree(response)
                    .path("merge_base_commit").path("sha").asText("");
            if (mergeBaseSha.isBlank()) {
                throw new IllegalStateException(
                        "GitHub comparison did not include a merge-base SHA.");
            }
            return mergeBaseSha;
        } catch (IOException e) {
            throw new IllegalStateException("GitHub returned invalid pull request comparison metadata.");
        }
    }

    private String getCompareDiff(String owner, String repo, String baseSha, String headSha) {
        try {
            String diff = webClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{base}...{head}",
                            owner, repo, baseSha, headSha)
                    .header("Accept", "application/vnd.github.diff")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            status -> reactor.core.publisher.Mono.error(
                                    new IllegalStateException(
                                            "Unable to retrieve the pull request snapshot diff from GitHub.")))
                    .bodyToMono(String.class)
                    .block();
            if (diff == null) {
                throw new IllegalStateException("GitHub returned no pull request snapshot diff.");
            }
            return diff;
        } catch (WebClientRequestException | WebClientResponseException e) {
            throw new IllegalStateException(
                    "Unable to retrieve the pull request snapshot diff from GitHub.");
        }
    }

    private PullRequestRefs getPullRequestRefs(String owner, String repo, String pullNumber) {
        String response = getJson(
                "/repos/{owner}/{repo}/pulls/{pull}",
                "pull request metadata",
                owner,
                repo,
                pullNumber);
        try {
            JsonNode pullRequest = objectMapper.readTree(response);
            String headSha = pullRequest.path("head").path("sha").asText("");
            String baseSha = pullRequest.path("base").path("sha").asText("");
            if (headSha.isBlank() || baseSha.isBlank()) {
                throw new IllegalStateException(
                        "GitHub pull request response did not include base and head SHAs.");
            }
            return new PullRequestRefs(baseSha, headSha);
        } catch (IOException e) {
            throw new IllegalStateException("GitHub returned invalid pull request metadata.");
        }
    }

    private static IllegalStateException pullRequestRevisionMismatch() {
        return new IllegalStateException(
                "Pull request base or head changed during analysis; retry the review.");
    }

    public List<SourceFile> getPullRequestSourceFiles(String owner, String repo, String pullNumber) {
        String headSha = getPullRequestHeadSha(owner, repo, pullNumber);
        return getPullRequestSourceFilesAtHead(owner, repo, pullNumber, headSha);
    }

    public List<SourceFile> getPullRequestSourceFiles(
            String owner,
            String repo,
            String pullNumber,
            String expectedHeadSha) {
        requirePullRequestHead(owner, repo, pullNumber, expectedHeadSha);
        return getPullRequestSourceFilesAtHead(owner, repo, pullNumber, expectedHeadSha);
    }

    private List<SourceFile> getPullRequestSourceFilesAtHead(
            String owner,
            String repo,
            String pullNumber,
            String expectedHeadSha) {
        List<JsonNode> changedFiles = getChangedFileMetadata(owner, repo, pullNumber);
        requirePullRequestHead(owner, repo, pullNumber, expectedHeadSha);

        List<SourceFile> sourceFiles = new ArrayList<>();
        long totalBytes = 0;
        for (JsonNode file : changedFiles) {
            String status = file.path("status").asText("");
            if ("removed".equals(status)) {
                continue;
            }

            String filename = file.path("filename").asText("");
            if (filename.isBlank()) {
                throw new IllegalStateException(
                        "GitHub returned changed-file metadata without a filename.");
            }
            validateRepositoryPath(filename);
            if (!isSupportedSourceFile(filename)) {
                continue;
            }

            long declaredSize = file.path("size").asLong(-1);
            if (declaredSize > MAX_SOURCE_FILE_BYTES) {
                throw new IllegalStateException(
                        "Changed source file exceeds the 1 MiB analysis limit: " + filename);
            }

            String content = getSourceFileContent(owner, repo, filename, expectedHeadSha);
            long contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
            if (contentBytes > MAX_SOURCE_FILE_BYTES) {
                throw new IllegalStateException(
                        "Changed source file exceeds the 1 MiB analysis limit: " + filename);
            }
            totalBytes += contentBytes;
            if (totalBytes > MAX_TOTAL_SOURCE_BYTES) {
                throw new IllegalStateException(
                        "Changed source files exceed the 10 MiB total analysis limit.");
            }
            sourceFiles.add(new SourceFile(filename, content));
        }
        return sourceFiles;
    }

    private void requirePullRequestHead(
            String owner,
            String repo,
            String pullNumber,
            String expectedHeadSha) {
        if (expectedHeadSha == null || expectedHeadSha.isBlank()
                || !expectedHeadSha.equalsIgnoreCase(getPullRequestHeadSha(owner, repo, pullNumber))) {
            throw new IllegalStateException(
                    "Pull request head does not match the requested analysis commit; retry the analysis.");
        }
    }

    private String getPullRequestHeadSha(String owner, String repo, String pullNumber) {
        String response = getJson(
                "/repos/{owner}/{repo}/pulls/{pull}",
                "pull request metadata",
                owner,
                repo,
                pullNumber);
        try {
            String sha = objectMapper.readTree(response).path("head").path("sha").asText("");
            if (sha.isBlank()) {
                throw new IllegalStateException("GitHub pull request response did not include a head SHA.");
            }
            return sha;
        } catch (IOException e) {
            throw new IllegalStateException("GitHub returned invalid pull request metadata.");
        }
    }

    private List<JsonNode> getChangedFileMetadata(String owner, String repo, String pullNumber) {
        List<JsonNode> files = new ArrayList<>();
        int page = 1;
        while (true) {
            String response = getJson(
                    "/repos/{owner}/{repo}/pulls/{pull}/files?per_page={perPage}&page={page}",
                    "pull request changed files",
                    owner,
                    repo,
                    pullNumber,
                    FILES_PER_PAGE,
                    page);
            try {
                JsonNode result = objectMapper.readTree(response);
                if (!result.isArray()) {
                    throw new IllegalStateException(
                            "GitHub returned an invalid pull request file list.");
                }
                int pageSize = result.size();
                result.forEach(files::add);
                if (pageSize < FILES_PER_PAGE) {
                    return files;
                }
            } catch (IOException e) {
                throw new IllegalStateException("GitHub returned invalid pull request file data.");
            }
            page++;
        }
    }

    private String getSourceFileContent(String owner, String repo, String filename, String headSha) {
        String[] pathSegments = filename.split("/");
        String response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> contentsUri(uriBuilder, owner, repo, pathSegments, headSha))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            status -> reactor.core.publisher.Mono.error(
                                    new IllegalStateException(
                                            "Unable to retrieve changed source file from GitHub.")))
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientRequestException | WebClientResponseException e) {
            throw new IllegalStateException(
                    "Unable to retrieve changed source file from GitHub.");
        }
        if (response == null) {
            throw new IllegalStateException("GitHub returned no content for a changed source file.");
        }

        try {
            JsonNode contentResponse = objectMapper.readTree(response);
            if (!"file".equals(contentResponse.path("type").asText())
                    || !"base64".equals(contentResponse.path("encoding").asText())
                    || !contentResponse.path("content").isTextual()) {
                throw new IllegalStateException(
                        "GitHub did not return text content for changed source file: " + filename);
            }

            byte[] contentBytes = Base64.getMimeDecoder().decode(
                    contentResponse.path("content").asText());
            if (contentBytes.length > MAX_SOURCE_FILE_BYTES) {
                throw new IllegalStateException(
                        "Changed source file exceeds the 1 MiB analysis limit: " + filename);
            }
            for (byte contentByte : contentBytes) {
                if (contentByte == 0) {
                    throw new IllegalStateException(
                            "Changed source file is binary and cannot be analyzed: " + filename);
                }
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contentBytes))
                    .toString();
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof CharacterCodingException) {
                throw new IllegalStateException(
                        "Changed source file is not valid UTF-8 text: " + filename);
            }
            throw new IllegalStateException("GitHub returned invalid content for a changed source file.");
        }
    }

    private String getJson(String uriTemplate, String operation, Object... uriVariables) {
        String response;
        try {
            response = webClient.get()
                    .uri(uriTemplate, uriVariables)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            status -> reactor.core.publisher.Mono.error(
                                    new IllegalStateException("GitHub request failed while retrieving "
                                            + operation + ".")))
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientRequestException | WebClientResponseException e) {
            throw new IllegalStateException(
                    "Unable to retrieve " + operation + " from GitHub.");
        }
        if (response == null) {
            throw new IllegalStateException("GitHub returned no data for " + operation + ".");
        }
        return response;
    }

    private static java.net.URI contentsUri(
            UriBuilder uriBuilder,
            String owner,
            String repo,
            String[] pathSegments,
            String headSha) {
        return uriBuilder.pathSegment("repos", owner, repo, "contents")
                .pathSegment(pathSegments)
                .queryParam("ref", headSha)
                .build();
    }

    private static boolean isSupportedSourceFile(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        return extensionIndex >= 0
                && SUPPORTED_SOURCE_EXTENSIONS.contains(
                        filename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT));
    }

    public static void validateRepositoryPath(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalStateException("GitHub returned an unsafe changed-file path.");
        }
        try {
            Path path = Path.of(filename);
            if (filename.isBlank()
                    || filename.indexOf('\\') >= 0
                    || filename.indexOf(':') >= 0
                    || path.isAbsolute()
                    || path.normalize().startsWith("..")
                    || !path.normalize().equals(path)
                    || path.toString().isBlank()) {
                throw new IllegalStateException("GitHub returned an unsafe changed-file path.");
            }
        } catch (InvalidPathException e) {
            throw new IllegalStateException("GitHub returned an invalid changed-file path.");
        }
    }

    public record SourceFile(String path, String content) {
    }

    public record PullRequestSnapshot(
            String diff,
            String baseSha,
            String headSha,
            String mergeBaseSha,
            List<ChangedFileSnapshot> changedFiles) {
    }

    public record ChangedFileSnapshot(
            PullRequestDiff.FileChanges changes,
            String oldContent,
            String newContent,
            boolean oldSupported,
            boolean newSupported) {
    }

    private record PullRequestRefs(String baseSha, String headSha) {
    }
     public record PullRequestContext(String title, String body) {
}
}