const MENU_ID = "ai-code-review-pr";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: MENU_ID,
    title: "Review with AI Code Review",
    contexts: ["link"],
    targetUrlPatterns: [
      "https://github.com/*/*/pull/*"
    ]
  });
});

chrome.contextMenus.onClicked.addListener((info) => {
  if (info.menuItemId !== MENU_ID) {
    return;
  }

  const pullRequestUrl = info.linkUrl;

  if (!pullRequestUrl) {
    return;
  }

  const githubPullRequestPattern =
    /^https:\/\/github\.com\/[^/]+\/[^/]+\/pull\/\d+\/?$/;

  if (!githubPullRequestPattern.test(pullRequestUrl)) {
    return;
  }

  const applicationUrl =
    "http://localhost:5173/?pr=" +
    encodeURIComponent(pullRequestUrl) +
    "&autoReview=true";

  chrome.tabs.create({
    url: applicationUrl
  });
});