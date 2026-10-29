import assert from "node:assert/strict";
import { test } from "node:test";
import { createReviewRequestStore } from "../src/services/reviewRequestStore.js";

class MemoryStorage {
  values = new Map();

  getItem(key) {
    return this.values.get(key) ?? null;
  }

  setItem(key, value) {
    this.values.set(key, value);
  }
}

function deferred() {
  let resolve;
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

test("keeps a single in-flight review result available after route subscribers leave", async () => {
  const storage = new MemoryStorage();
  const pendingRequest = deferred();
  let requestCount = 0;
  let completionEvents = 0;
  const store = createReviewRequestStore(
    () => {
      requestCount += 1;
      return pendingRequest.promise;
    },
    storage,
    () => {
      completionEvents += 1;
    }
  );
  const unsubscribe = store.subscribe(() => {});

  const firstSubmission = store.submit(
    "https://github.com/acme/service/pull/42",
    { pullRequestId: "42" }
  );
  unsubscribe();
  const repeatedSubmission = store.submit(
    "https://github.com/acme/service/pull/42",
    { pullRequestId: "42" }
  );

  assert.strictEqual(repeatedSubmission, firstSubmission);
  assert.equal(store.getState().status, "processing");
  assert.equal(requestCount, 0);

  const review = { id: 17, reviewText: "{}" };
  pendingRequest.resolve(review);
  assert.strictEqual(await firstSubmission, review);

  assert.equal(requestCount, 1);
  assert.equal(completionEvents, 1);
  assert.equal(store.getState().status, "completed");
  assert.strictEqual(store.getState().review, review);
  assert.equal(JSON.parse(storage.getItem("ai-code-review:latest-review")).status, "completed");
});

test("does not restore a persisted in-flight request as an active spinner after reload", async () => {
  const storage = new MemoryStorage();
  storage.setItem("ai-code-review:latest-review", JSON.stringify({
    pullRequestUrl: "https://github.com/acme/service/pull/42",
    status: "processing",
    review: null,
    submittedAt: Date.now()
  }));
  let requestCount = 0;
  const store = createReviewRequestStore(
    () => {
      requestCount += 1;
      return Promise.resolve({});
    },
    storage
  );

  assert.equal(store.getState().status, "interrupted");
  assert.match(store.getState().errorMessage, /Check Review History/);
  await assert.rejects(
    store.submit(
      "https://github.com/acme/service/pull/42",
      { pullRequestId: "42" }
    ),
    /Check Review History/
  );
  assert.equal(requestCount, 0);

  await store.submit(
    "https://github.com/acme/service/pull/42",
    { pullRequestId: "42" },
    { allowInterruptedRetry: true }
  );
  assert.equal(requestCount, 1);
});

test("treats a lost HTTP response as an unknown outcome and blocks automatic duplicate retries", async () => {
  let requestCount = 0;
  const store = createReviewRequestStore(() => {
    requestCount += 1;
    return Promise.reject(new Error("Connection reset"));
  }, new MemoryStorage());

  await assert.rejects(
    store.submit(
      "https://github.com/acme/service/pull/42",
      { pullRequestId: "42" }
    )
  );

  assert.equal(store.getState().status, "interrupted");
  assert.match(store.getState().errorMessage, /may still be processing/);
  assert.equal(requestCount, 1);
  await assert.rejects(
    store.submit(
      "https://github.com/acme/service/pull/42",
      { pullRequestId: "42" }
    ),
    /Check Review History/
  );
  assert.equal(requestCount, 1);
});
