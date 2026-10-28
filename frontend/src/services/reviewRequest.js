import { createReview } from "./api";
import { createReviewRequestStore } from "./reviewRequestStore";

function getSessionStorage() {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

const reviewRequestStore = createReviewRequestStore(
  createReview,
  getSessionStorage(),
  () => window.dispatchEvent(new Event("reviews:updated"))
);

export const getReviewRequestState = () => reviewRequestStore.getState();
export const subscribeToReviewRequest = (listener) => reviewRequestStore.subscribe(listener);
export const submitReviewRequest = (url, request, options) => reviewRequestStore.submit(url, request, options);
export const clearReviewRequestError = () => reviewRequestStore.clearError();
