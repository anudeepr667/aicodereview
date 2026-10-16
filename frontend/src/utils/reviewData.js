export function orderReviews(reviews) {
  return [...(Array.isArray(reviews) ? reviews : [])].sort((left, right) => {
    const leftId = Number(left.id);
    const rightId = Number(right.id);
    if (Number.isFinite(leftId) && Number.isFinite(rightId)) return rightId - leftId;
    return 0;
  });
}

export function repositoryName(review) {
  return review.repositoryName || "Unknown repository";
}

export function parseReviewText(reviewText) {
  if (typeof reviewText !== "string" || !reviewText.trim()) return null;
  try {
    const parsed = JSON.parse(reviewText);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
    return {
      ...parsed,
      categoryReviews: Array.isArray(parsed.categoryReviews) ? parsed.categoryReviews : [],
      findings: Array.isArray(parsed.findings) ? parsed.findings : [],
      positiveAspects: Array.isArray(parsed.positiveAspects) ? parsed.positiveAspects : [],
    };
  } catch {
    return null;
  }
}

export function reviewTextValue(value, fallback) {
  return typeof value === "string" && value.trim() ? value : fallback;
}

export function reviewDate(review) {
  const value = review.createdAt || review.reviewedAt || review.timestamp || review.createdDate;
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

export function repositoryCounts(reviews) {
  return reviews.reduce((counts, review) => {
    const name = repositoryName(review);
    counts[name] = (counts[name] || 0) + 1;
    return counts;
  }, {});
}