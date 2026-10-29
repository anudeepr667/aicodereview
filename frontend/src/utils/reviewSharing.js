export function createReviewShareDetails(review, origin) {
  const reviewId = review?.id;
  if (reviewId === null || reviewId === undefined || !String(reviewId).trim()) {
    return null;
  }

  const reviewUrl = new URL("/history", origin);
  reviewUrl.searchParams.set("review", String(reviewId));

  const repository = typeof review.repositoryName === "string" && review.repositoryName.trim()
    ? review.repositoryName.trim()
    : "Unknown repository";
  const pullRequestId = review.pullRequestId ?? review.pullRequestNumber;
  const identification = pullRequestId !== null && pullRequestId !== undefined && String(pullRequestId).trim()
    ? `${repository} pull request #${pullRequestId}`
    : repository;

  return {
    title: `AI Code Review: ${identification}`,
    text: `Saved AI review for ${identification}.`,
    url: reviewUrl.toString()
  };
}

export async function copyReviewLink(details, clipboard) {
  if (!details?.url || typeof clipboard?.writeText !== "function") {
    throw new Error("Clipboard access is unavailable.");
  }

  await clipboard.writeText(details.url);
  return "copied";
}

export async function shareReviewLink(details, browserNavigator) {
  if (!details?.url) {
    throw new Error("A saved review link is unavailable.");
  }

  if (typeof browserNavigator?.share === "function") {
    try {
      await browserNavigator.share({
        title: details.title,
        text: details.text,
        url: details.url
      });
      return "shared";
    } catch (error) {
      if (error?.name === "AbortError") {
        return "cancelled";
      }
      throw error;
    }
  }

  return copyReviewLink(details, browserNavigator?.clipboard);
}
