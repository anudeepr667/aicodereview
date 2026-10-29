export const REVIEW_CATEGORY_ORDER = [
  "Bugs & Logical Assessment",
  "Security Vulnerabilities",
  "Performance Improvements",
  "Code Quality and Readability",
  "Best Practices"
];

const CATEGORY_ALIASES = new Map([
  ["Bugs & Logical Errors", "Bugs & Logical Assessment"],
  ["Bug & Logic Assessment", "Bugs & Logical Assessment"],
  ["Bugs & Logic Assessment", "Bugs & Logical Assessment"],
  ["Bug & Logical Assessment", "Bugs & Logical Assessment"],
  ["Java and Framework Best Practices", "Best Practices"]
]);

export function normalizeCategoryName(category) {
  if (typeof category !== "string") return null;
  const normalized = category.trim();
  if (REVIEW_CATEGORY_ORDER.includes(normalized)) return normalized;
  return CATEGORY_ALIASES.get(normalized) || null;
}

export function buildCategoryReviews(categoryReviews) {
  const explanations = new Map(REVIEW_CATEGORY_ORDER.map((category) => [category, []]));
  const seen = new Map(REVIEW_CATEGORY_ORDER.map((category) => [category, new Set()]));

  if (Array.isArray(categoryReviews)) {
    for (const item of categoryReviews) {
      const category = normalizeCategoryName(item?.category);
      const explanation = typeof item?.review === "string" ? item.review.trim() : "";
      if (!category || !explanation) continue;

      const key = explanation.replace(/\s+/g, " ");
      if (seen.get(category).has(key)) continue;

      seen.get(category).add(key);
      explanations.get(category).push(explanation);
    }
  }

  return REVIEW_CATEGORY_ORDER.map((category) => ({
    category,
    review: explanations.get(category).join("\n\n")
  }));
}

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

export function githubPullRequestUrl(review) {
  const repository = review?.repositoryName;
  const pullRequestId = review?.pullRequestId ?? review?.pullRequestNumber;
  if (
    typeof repository !== "string" ||
    (typeof pullRequestId !== "string" && typeof pullRequestId !== "number")
  ) {
    return null;
  }

  const [owner, name, ...extra] = repository.split("/");
  if (
    extra.length ||
    !owner ||
    !name ||
    owner === "." ||
    owner === ".." ||
    name === "." ||
    name === ".." ||
    !/^[A-Za-z0-9_.-]+$/.test(owner) ||
    !/^[A-Za-z0-9_.-]+$/.test(name) ||
    !/^[1-9]\d*$/.test(String(pullRequestId))
  ) {
    return null;
  }

  return `https://github.com/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/pull/${pullRequestId}`;
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