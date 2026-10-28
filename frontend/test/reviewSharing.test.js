import assert from "node:assert/strict";
import { test } from "node:test";
import {
  copyReviewLink,
  createReviewShareDetails,
  shareReviewLink
} from "../src/utils/reviewSharing.js";

test("builds a hosted authenticated History link with repository and pull request context", () => {
  const details = createReviewShareDetails({
    id: 42,
    repositoryName: "octo/project",
    pullRequestId: "2982"
  }, "https://reviews.example.com");

  assert.equal(details.url, "https://reviews.example.com/history?review=42");
  assert.equal(details.title, "AI Code Review: octo/project pull request #2982");
  assert.equal(details.text, "Saved AI review for octo/project pull request #2982.");
});

test("does not produce a share link for an unsaved review", () => {
  assert.equal(createReviewShareDetails({ repositoryName: "octo/project" }, "https://reviews.example.com"), null);
});

test("uses Web Share when supported without copying the link", async () => {
  const details = createReviewShareDetails({
    id: 7,
    repositoryName: "octo/project",
    pullRequestId: 12
  }, "https://reviews.example.com");
  let shared;
  let copied = false;

  const result = await shareReviewLink(details, {
    share: async (payload) => { shared = payload; },
    clipboard: { writeText: async () => { copied = true; } }
  });

  assert.equal(result, "shared");
  assert.deepEqual(shared, {
    title: "AI Code Review: octo/project pull request #12",
    text: "Saved AI review for octo/project pull request #12.",
    url: "https://reviews.example.com/history?review=7"
  });
  assert.equal(copied, false);
});

test("copies the authenticated review link when Web Share is unsupported", async () => {
  const details = createReviewShareDetails({ id: 9, repositoryName: "octo/project" }, "https://reviews.example.com");
  let copiedUrl;

  const result = await shareReviewLink(details, {
    clipboard: { writeText: async (url) => { copiedUrl = url; } }
  });

  assert.equal(result, "copied");
  assert.equal(copiedUrl, "https://reviews.example.com/history?review=9");
});

test("copies the link directly when the Copy link action is used", async () => {
  const details = createReviewShareDetails({ id: 15 }, "https://reviews.example.com");
  let copiedUrl;

  assert.equal(await copyReviewLink(details, {
    writeText: async (url) => { copiedUrl = url; }
  }), "copied");
  assert.equal(copiedUrl, "https://reviews.example.com/history?review=15");
});

test("reports user cancellation without treating it as a sharing failure", async () => {
  const details = createReviewShareDetails({ id: 3 }, "https://reviews.example.com");
  const result = await shareReviewLink(details, {
    share: async () => { throw Object.assign(new Error("cancelled"), { name: "AbortError" }); }
  });

  assert.equal(result, "cancelled");
});
