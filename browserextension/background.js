const MENU_ID = "ai-code-review-pr";

chrome.runtime.onInstalled.addListener(() => {

  chrome.contextMenus.removeAll(() => {

    chrome.contextMenus.create({
      id: MENU_ID,
      title: "Review with AI Code Review",
      contexts: ["link"],
      targetUrlPatterns: [
        "https://github.com/*/*/pull/*"
      ]
    });

  });

});


chrome.contextMenus.onClicked.addListener((info) => {

  if (info.menuItemId !== MENU_ID) {
    return;
  }

  if (!info.linkUrl) {
    return;
  }

  try {

    const url = new URL(info.linkUrl);

    if (url.hostname !== "github.com") {
      return;
    }

    const pullRequestPattern =
      /^\/[^/]+\/[^/]+\/pull\/\d+\/?$/;

    if (!pullRequestPattern.test(url.pathname)) {
      return;
    }

    const pullRequestUrl =
      url.origin + url.pathname;

    const applicationUrl =
      "http://localhost:5173/?pr=" +
      encodeURIComponent(pullRequestUrl) +
      "&autoReview=true";

    chrome.tabs.create({
      url: applicationUrl
    });

  } catch (error) {

    console.error(
      "Unable to process GitHub pull request URL:",
      error
    );

  }

});