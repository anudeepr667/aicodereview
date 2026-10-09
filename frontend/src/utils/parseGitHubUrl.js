export function parseGitHubPullRequestUrl(value) {
  try {
    const url = new URL(value.trim());
    const parts = url.pathname.split("/").filter(Boolean);

    if (
      url.protocol !== "https:" ||
      url.hostname.toLowerCase() !== "github.com" ||
      parts.length !== 4 ||
      parts[2] !== "pull" ||
      !/^[1-9]\d*$/.test(parts[3])
    ) {
      return null;
    }

    return { owner: parts[0], repo: parts[1], pullRequestId: parts[3] };
  } catch {
    return null;
  }
}
