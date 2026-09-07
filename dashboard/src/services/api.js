const API_BASE_URL = "http://localhost:8080";

export async function analyzePullRequest(pullRequestUrl) {
  const response = await fetch(
    `${API_BASE_URL}/api/pull-requests/analyze`,
    {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify({
        pullRequestUrl: pullRequestUrl,
      }),
    }
  );

  if (!response.ok) {
    const errorMessage = await response.text();
    throw new Error(
      errorMessage || "Failed to analyze pull request."
    );
  }

  return response.json();
}

export async function getReviews() {
  const response = await fetch(`${API_BASE_URL}/reviews`);

  if (!response.ok) {
    throw new Error("Failed to load review history.");
  }

  return response.json();
}