const REVIEW_SESSION_KEY = "ai-code-review:latest-review";

function readSavedState(storage) {
  try {
    const rawValue = storage?.getItem(REVIEW_SESSION_KEY);
    if (!rawValue) return null;

    const state = JSON.parse(rawValue);
    if (!state || typeof state !== "object") return null;

    if (!state.status) {
      state.status = state.review ? "completed" : state.isLoading ? "processing" : "idle";
    }

    if (state.status === "processing") {
      return {
        ...state,
        status: "interrupted",
        errorMessage: "The browser no longer has the review response. Check Review History before starting another review."
      };
    }

    return state;
  } catch {
    return null;
  }
}

function requestErrorMessage(error) {
  if (!error.response) {
    return "The connection ended before the review was confirmed. It may still be processing. Check Review History before retrying.";
  }

  const responseData = error.response.data;
  if (typeof responseData === "string" && responseData.trim()) {
    return responseData;
  }

  if (responseData && typeof responseData === "object") {
    const message = responseData.message || responseData.error;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  return `Unable to analyze this pull request (HTTP ${error.response.status}).`;
}

export function createReviewRequestStore(createReview, storage, onReviewCompleted) {
  const listeners = new Set();
  const inFlightRequests = new Map();
  let state = readSavedState(storage) || {
    pullRequestUrl: "",
    status: "idle",
    review: null,
    errorMessage: ""
  };

  function publish(nextState) {
    state = nextState;
    try {
      storage?.setItem(REVIEW_SESSION_KEY, JSON.stringify(nextState));
    } catch {
      // Keep the in-memory request state available if session storage is disabled.
    }
    listeners.forEach((listener) => listener(state));
  }

  return {
    getState() {
      return state;
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    submit(pullRequestUrl, request, { allowInterruptedRetry = false } = {}) {
      const existingRequest = inFlightRequests.get(pullRequestUrl);
      if (existingRequest) {
        return existingRequest;
      }

      if (state.pullRequestUrl === pullRequestUrl
        && state.status === "interrupted"
        && !allowInterruptedRetry) {
        return Promise.reject(new Error(
          "A previous review may still be processing. Check Review History before retrying."
        ));
      }

      const requestStartedAt = performance.now();
      publish({
        pullRequestUrl,
        status: "processing",
        review: null,
        errorMessage: "",
        submittedAt: Date.now()
      });

      const promise = Promise.resolve()
        .then(() => createReview(request))
        .then((review) => {
          console.info(
            "Review timing:",
            JSON.stringify({
              source: "browser",
              stage: "review_http_round_trip",
              durationMs: Math.round(performance.now() - requestStartedAt),
              outcome: "success",
              reviewId: review?.id ?? null
            })
          );
          if (state.pullRequestUrl === pullRequestUrl && state.status === "processing") {
            publish({
              pullRequestUrl,
              status: "completed",
              review,
              errorMessage: "",
              submittedAt: state.submittedAt
            });
          }
          onReviewCompleted?.();
          return review;
        })
        .catch((error) => {
          console.info(
            "Review timing:",
            JSON.stringify({
              source: "browser",
              stage: "review_http_round_trip",
              durationMs: Math.round(performance.now() - requestStartedAt),
              outcome: "failure"
            })
          );
          if (state.pullRequestUrl === pullRequestUrl && state.status === "processing") {
            publish({
              pullRequestUrl,
              status: error.response ? "failed" : "interrupted",
              review: null,
              errorMessage: requestErrorMessage(error),
              submittedAt: state.submittedAt
            });
          }
          throw error;
        })
        .finally(() => {
          inFlightRequests.delete(pullRequestUrl);
        });

      inFlightRequests.set(pullRequestUrl, promise);
      return promise;
    },

    clearError() {
      if (state.status === "failed") {
        publish({
          ...state,
          status: "idle",
          errorMessage: ""
        });
      }
    }
  };
}
