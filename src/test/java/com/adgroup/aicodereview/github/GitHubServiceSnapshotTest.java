package com.adgroup.aicodereview.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubServiceSnapshotTest {

    private static final String BASE_SHA = "base0123456789";
    private static final String HEAD_SHA = "head0123456789";
    private static final String MERGE_BASE_SHA = "merge0123456789";
    private static final String DIFF = """
            diff --git a/src/App.java b/src/App.java
            --- a/src/App.java
            +++ b/src/App.java
            @@ -0,0 +1,1 @@
            +class App {}
            """;

    @Test
    void retrievesDiffAndSourceFromOneCapturedBaseAndHeadPair() {
        AtomicInteger metadataRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                metadataRequests.incrementAndGet();
                return jsonResponse(pullRequestJson(BASE_SHA, HEAD_SHA));
            }
            if (path.equals("/repos/owner/repo/compare/" + BASE_SHA + "..." + HEAD_SHA)) {
                if ("application/vnd.github.diff".equals(request.headers().getFirst("Accept"))) {
                    return jsonResponse(DIFF);
                }
                return jsonResponse(compareJson(MERGE_BASE_SHA));
            }
            if (path.equals("/repos/owner/repo/contents/src/App.java")) {
                assertEquals("ref=" + HEAD_SHA, request.url().getQuery());
                return jsonResponse(contentJson("class App {}"));
            }
            return notFoundResponse();
        };

        GitHubService.PullRequestSnapshot snapshot =
                service(exchange).getPullRequestSnapshot("owner", "repo", "42", null);

        assertEquals(2, metadataRequests.get());
        assertEquals(BASE_SHA, snapshot.baseSha());
        assertEquals(HEAD_SHA, snapshot.headSha());
        assertEquals(MERGE_BASE_SHA, snapshot.mergeBaseSha());
        assertEquals(DIFF, snapshot.diff());
        assertEquals("class App {}", snapshot.changedFiles().getFirst().newContent());
    }

    @Test
    void rejectsSnapshotWhenBaseChangesDuringRetrieval() {
        AtomicInteger metadataRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                int requestNumber = metadataRequests.incrementAndGet();
                return jsonResponse(requestNumber == 1
                        ? pullRequestJson(BASE_SHA, HEAD_SHA)
                        : pullRequestJson("new-base-sha", HEAD_SHA));
            }
            if (path.equals("/repos/owner/repo/compare/" + BASE_SHA + "..." + HEAD_SHA)) {
                if ("application/vnd.github.diff".equals(request.headers().getFirst("Accept"))) {
                    return jsonResponse(DIFF);
                }
                return jsonResponse(compareJson(MERGE_BASE_SHA));
            }
            if (path.equals("/repos/owner/repo/contents/src/App.java")) {
                assertEquals("ref=" + HEAD_SHA, request.url().getQuery());
                return jsonResponse(contentJson("class App {}"));
            }
            return notFoundResponse();
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSnapshot("owner", "repo", "42", null));

        assertEquals(
                "Pull request base or head changed during analysis; retry the review.",
                error.getMessage());
        assertEquals(2, metadataRequests.get());
    }

    @Test
    void rejectsSnapshotWhenHeadChangesDuringRetrieval() {
        AtomicInteger metadataRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                int requestNumber = metadataRequests.incrementAndGet();
                return jsonResponse(requestNumber == 1
                        ? pullRequestJson(BASE_SHA, HEAD_SHA)
                        : pullRequestJson(BASE_SHA, "new-head-sha"));
            }
            if (path.equals("/repos/owner/repo/compare/" + BASE_SHA + "..." + HEAD_SHA)) {
                if ("application/vnd.github.diff".equals(request.headers().getFirst("Accept"))) {
                    return jsonResponse(DIFF);
                }
                return jsonResponse(compareJson(MERGE_BASE_SHA));
            }
            if (path.equals("/repos/owner/repo/contents/src/App.java")) {
                assertEquals("ref=" + HEAD_SHA, request.url().getQuery());
                return jsonResponse(contentJson("class App {}"));
            }
            return notFoundResponse();
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSnapshot("owner", "repo", "42", null));

        assertEquals(
                "Pull request base or head changed during analysis; retry the review.",
                error.getMessage());
        assertEquals(2, metadataRequests.get());
    }

    @Test
    void rejectsWebhookSnapshotWhenEventHeadDoesNotMatchCurrentHead() {
        ExchangeFunction exchange = request -> {
            if (request.url().getPath().equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse(pullRequestJson(BASE_SHA, HEAD_SHA));
            }
            throw new AssertionError("A mismatched webhook head must be rejected before diff retrieval.");
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(exchange).getPullRequestSnapshot(
                        "owner", "repo", "42", "different-head"));

        assertEquals(
                "Pull request base or head changed during analysis; retry the review.",
                error.getMessage());
    }

    @Test
    void retrievesRenamedFileOldSideFromMergeBaseAndNewSideFromHead() {
        String renameDiff = """
                diff --git a/src/Old.java b/src/New.java
                similarity index 80%
                rename from src/Old.java
                rename to src/New.java
                --- a/src/Old.java
                +++ b/src/New.java
                @@ -4,1 +4,1 @@
                -old line
                +new line
                """;
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if (path.equals("/repos/owner/repo/pulls/42")) {
                return jsonResponse(pullRequestJson(BASE_SHA, HEAD_SHA));
            }
            if (path.equals("/repos/owner/repo/compare/" + BASE_SHA + "..." + HEAD_SHA)) {
                if ("application/vnd.github.diff".equals(request.headers().getFirst("Accept"))) {
                    return jsonResponse(renameDiff);
                }
                return jsonResponse(compareJson(MERGE_BASE_SHA));
            }
            if (path.equals("/repos/owner/repo/contents/src/Old.java")) {
                assertEquals("ref=" + MERGE_BASE_SHA, request.url().getQuery());
                return jsonResponse(contentJson("one\ntwo\nthree\nold line\n"));
            }
            if (path.equals("/repos/owner/repo/contents/src/New.java")) {
                assertEquals("ref=" + HEAD_SHA, request.url().getQuery());
                return jsonResponse(contentJson("one\ntwo\nthree\nnew line\n"));
            }
            return notFoundResponse();
        };

        GitHubService.PullRequestSnapshot snapshot =
                service(exchange).getPullRequestSnapshot("owner", "repo", "42", HEAD_SHA);

        GitHubService.ChangedFileSnapshot changedFile = snapshot.changedFiles().getFirst();
        assertEquals("src/Old.java", changedFile.changes().oldPath());
        assertEquals("src/New.java", changedFile.changes().newPath());
        assertEquals("one\ntwo\nthree\nold line\n", changedFile.oldContent());
        assertEquals("one\ntwo\nthree\nnew line\n", changedFile.newContent());
    }

    private GitHubService service(ExchangeFunction exchange) {
        WebClient client = WebClient.builder()
                .baseUrl("https://api.github.com")
                .exchangeFunction(exchange)
                .build();
        return new GitHubService(client, new ObjectMapper());
    }

    private static String pullRequestJson(String baseSha, String headSha) {
        return "{\"base\":{\"sha\":\"" + baseSha + "\"},\"head\":{\"sha\":\"" + headSha + "\"}}";
    }

    private static String compareJson(String mergeBaseSha) {
        return "{\"merge_base_commit\":{\"sha\":\"" + mergeBaseSha + "\"}}";
    }

    private static String contentJson(String content) {
        String encoded = Base64.getEncoder()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
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
