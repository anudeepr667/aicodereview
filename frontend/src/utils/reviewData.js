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

export function repositoryCounts(reviews) {
  return reviews.reduce((counts, review) => {
    const name = repositoryName(review);
    counts[name] = (counts[name] || 0) + 1;
    return counts;
  }, {});
}