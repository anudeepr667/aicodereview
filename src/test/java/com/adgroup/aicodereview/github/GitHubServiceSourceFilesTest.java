package com.adgroup.aicodereview.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubServiceSourceFilesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsConfiguredSourceExtensionsAndRejectsUnsupportedExtensions() {
        List<String> supportedExtensions = List.of(
                ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".go", ".rb", ".php",
                ".c", ".h", ".cc", ".cpp", ".cxx", ".hpp", ".cs", ".kt");
        List<String> unsupportedExtensions = List.of(
                ".md", ".txt", ".json", ".java.txt", ".mjs", ".swift", ".rs");

        for (String extension : supportedExtensions) {
            AtomicInteger contentRequests = new AtomicInteger();
            List<GitHubService.SourceFile> files = serviceForSingleChangedFile(
                    "source" + extension, contentRequests).getPullRequestSourceFiles(
                    "owner", "repo", "42");

            assertEquals(1, files.size(), "Expected supported extension " + extension);
            assertEquals("source" + extension, files.get(0).path());
            assertEquals(1, contentRequests.get());
        }

        for (String extension : unsupportedExtensions) {
            AtomicInteger contentRequests = new AtomicInteger();
            List<GitHubService.SourceFile> files = serviceForSingleChangedFile(
                    "source" + extension, contentRequests).getPullRequestSourceFiles(
                    "owner", "repo", "42");

            assertEquals(List.of(), files, "Expected unsupported extension " + extension);
            assertEquals(0, contentRequests.get());
        }
    }

    @Test
    void retrievesChangedSourceContentAtTheRequestedPullRequestHead() {
        String expectedHead = "0123456789abcdef0123456789abcdef01234567";
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"" + expectedHead + "\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                return jsonResponse(
                        "[{\"filename\":\"src/App.java\",\"status\":\"modified\",\"size\":14}]");
            }
            if (path.endsWith("/contents/src/App.java")) {
                assertEquals("ref=" + expectedHead, request.url().getQuery());
                return jsonResponse(contentJson("class App {}"));
            }
            return notFoundResponse();
        };

        List<GitHubService.SourceFile> files = service(exchange).getPullRequestSourceFiles(
                "owner", "repo", "42", expectedHead);

        assertEquals(List.of(new GitHubService.SourceFile("src/App.java", "class App {}")), files);
    }

    @Test
    void rejectsDiffWhenPullRequestHeadDoesNotMatchWebhookCommit() {
        String expectedHead = "0123456789abcdef0123456789abcdef01234567";
        String newerHead = "fedcba9876543210fedcba9876543210fedcba98";
        AtomicInteger diffRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                if ("application/vnd.github.diff".equals(request.headers().getFirst("Accept"))) {
                    diffRequests.incrementAndGet();
                    return jsonResponse("diff --git a/file.java b/file.java");
                }
                return jsonResponse("{\"head\":{\"sha\":\"" + newerHead + "\"}}");
            }
            return notFoundResponse();
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestDiff(
                        "owner", "repo", "42", expectedHead));

        assertEquals(
                "Pull request head does not match the requested analysis commit; retry the analysis.",
                error.getMessage());
        assertEquals(0, diffRequests.get());
    }

    @Test
    void excludesDeletedAndUnsupportedFilesAndPinsContentToHeadSha() {
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"head-sha\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                return jsonResponse("""
                        [
                          {"filename":"src/app.py","status":"modified","size":14},
                          {"filename":"src/renamed.py","status":"renamed","size":14},
                          {"filename":"src/old.py","status":"removed","size":10},
                          {"filename":"README.md","status":"modified","size":20}
                        ]
                        """);
            }
            if (path.endsWith("/contents/src/app.py")) {
                assertEquals("head-sha", request.url().getQuery().substring(4));
                return jsonResponse(contentJson("print('hi')\n"));
            }
            if (path.endsWith("/contents/src/renamed.py")) {
                assertEquals("head-sha", request.url().getQuery().substring(4));
                return jsonResponse(contentJson("print('renamed')\n"));
            }
            return notFoundResponse();
        };

        List<GitHubService.SourceFile> files = service(exchange).getPullRequestSourceFiles(
                "owner", "repo", "42");

        assertEquals(2, files.size());
        assertEquals("src/app.py", files.get(0).path());
        assertEquals("print('hi')\n", files.get(0).content());
        assertEquals("src/renamed.py", files.get(1).path());
        assertEquals("print('renamed')\n", files.get(1).content());
    }

    @Test
    void paginatesFileMetadataAndUsesHeadShaForContents() {
        AtomicInteger secondPageRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"stable-sha\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                if (request.url().getQuery().endsWith("page=1")) {
                    ArrayNode files = objectMapper.createArrayNode();
                    for (int index = 0; index < 100; index++) {
                        ObjectNode file = files.addObject();
                        file.put("filename", "docs/file" + index + ".md");
                        file.put("status", "modified");
                        file.put("size", 10);
                    }
                    return jsonResponse(files.toString());
                }
                secondPageRequests.incrementAndGet();
                return jsonResponse(
                        "[{\"filename\":\"source.java\",\"status\":\"added\",\"size\":14}]");
            }
            if (path.endsWith("/contents/source.java")) {
                assertEquals("ref=stable-sha", request.url().getQuery());
                return jsonResponse(contentJson("class Source {}"));
            }
            return notFoundResponse();
        };

        List<GitHubService.SourceFile> files = service(exchange).getPullRequestSourceFiles(
                "owner", "repo", "42");

        assertEquals(1, secondPageRequests.get());
        assertEquals(List.of(new GitHubService.SourceFile("source.java", "class Source {}")), files);
    }

    @Test
    void rejectsUnsafeRepositoryPathsBeforeFetchingContents() {
        AtomicInteger contentRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"head-sha\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                return jsonResponse(
                        "[{\"filename\":\"../outside.py\",\"status\":\"modified\",\"size\":20}]");
            }
            contentRequests.incrementAndGet();
            return notFoundResponse();
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSourceFiles("owner", "repo", "42"));

        assertEquals("GitHub returned an unsafe changed-file path.", error.getMessage());
        assertEquals(0, contentRequests.get());
    }

    @Test
    void reportsChangedSourceRetrievalFailures() {
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"head-sha\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                return jsonResponse(
                        "[{\"filename\":\"src/app.py\",\"status\":\"modified\",\"size\":10}]");
            }
            return notFoundResponse();
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSourceFiles("owner", "repo", "42"));

        assertEquals("Unable to retrieve changed source file from GitHub.", error.getMessage());
    }

    @Test
    void reportsFilesExceedingTheDeclaredSizeLimit() {
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"head-sha\"}}");
            }
            return jsonResponse(
                    "[{\"filename\":\"src/large.py\",\"status\":\"modified\",\"size\":1048577}]");
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSourceFiles("owner", "repo", "42"));

        assertEquals(
                "Changed source file exceeds the 1 MiB analysis limit: src/large.py",
                error.getMessage());
    }

    @Test
    void rejectsBinaryChangedSourceFiles() {
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"head-sha\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                return jsonResponse(
                        "[{\"filename\":\"src/app.py\",\"status\":\"modified\",\"size\":4}]");
            }
            return jsonResponse(contentJson("\u0000\u0001\u0002\u0003"));
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSourceFiles("owner", "repo", "42"));

        assertEquals(
                "Changed source file is binary and cannot be analyzed: src/app.py",
                error.getMessage());
    }

    private GitHubService service(ExchangeFunction exchange) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .exchangeFunction(exchange)
                .build();
        return new GitHubService(webClient, objectMapper);
    }

    private GitHubService serviceForSingleChangedFile(
            String filename,
            AtomicInteger contentRequests) {
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse("{\"head\":{\"sha\":\"head-sha\"}}");
            }
            if (path.endsWith("/pulls/42/files")) {
                return jsonResponse("[{\"filename\":\"" + filename
                        + "\",\"status\":\"modified\",\"size\":12}]");
            }
            if (path.endsWith("/contents/" + filename)) {
                contentRequests.incrementAndGet();
                return jsonResponse(contentJson("source content"));
            }
            return notFoundResponse();
        };
        return service(exchange);
    }

    private static String contentJson(String content) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return "{\"type\":\"file\",\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}";
    }

    private static Mono<ClientResponse> jsonResponse(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build());
    }

    private static Mono<ClientResponse> notFoundResponse() {
        return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("{\"message\":\"not found\"}")
                .build());
    }
}
