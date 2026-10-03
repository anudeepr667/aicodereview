import { Link, useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  ArrowLeft,
  GitPullRequest,
  FileCode2,
  Plus,
  Minus,
} from "lucide-react";

import Sidebar from "../components/Sidebar";
import { reviewDetails } from "../data/mockData";
import { getReviews } from "../services/api";

function renderReviewText(text) {
  if (!text) {
    return <p>No AI review text was returned.</p>;
  }

  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const elements = [];
  let currentList = [];
  let listType = null;

  function flushList() {
    if (currentList.length === 0) {
      return;
    }

    if (listType === "ul") {
      elements.push(
        <ul key={`list-${elements.length}`}>
          {currentList.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ul>
      );
    }

    if (listType === "ol") {
      elements.push(
        <ol key={`list-${elements.length}`}>
          {currentList.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ol>
      );
    }

    currentList = [];
    listType = null;
  }

  lines.forEach((line, index) => {
    const heading = line.match(/^#{1,4}\s+(.+)/);
    const bullet = line.match(/^[-*]\s+(.+)/);
    const numbered = line.match(/^\d+[.)]\s+(.+)/);

    if (heading) {
      flushList();

      elements.push(
        <h3 key={`heading-${index}`}>
          {heading[1]}
        </h3>
      );

      return;
    }

    if (numbered && /^[-*]\s+/.test(lines[index + 1] || "")) {
      flushList();

      elements.push(
        <h3 key={`heading-${index}`}>
          {numbered[0]}
        </h3>
      );

      return;
    }

    if (bullet) {
      if (listType !== "ul") {
        flushList();
        listType = "ul";
      }

      currentList.push(bullet[1]);
      return;
    }

    if (numbered) {
      if (listType !== "ol") {
        flushList();
        listType = "ol";
      }

      currentList.push(numbered[1]);
      return;
    }

    flushList();

    elements.push(
      <p key={`paragraph-${index}`}>
        {line}
      </p>
    );
  });

  flushList();

  return elements;
}
function ReviewDetails() {
  const { id } = useParams();
  const [review, setReview] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function loadReview() {
      try {
        const reviews = await getReviews();
        setReview(reviews.find((item) => String(item.id) === id) || null);
      } catch {
        setReview(null);
      } finally {
        setIsLoading(false);
      }
    }

    loadReview();
  }, [id]);

  const fallbackReview = reviewDetails.find(
    (item) => item.id === Number(id)
  );
  const displayedReview = review || fallbackReview;

  if (isLoading) {
    return <div className="not-found">Loading review...</div>;
  }

  if (!displayedReview) {
    return (
      <div className="not-found">
        <h2>Review not found</h2>

        <Link to="/">
          Back to Dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="app-layout">

      <Sidebar />

      <main className="main-content">

        <Link to="/" className="back-link">
          <ArrowLeft size={18} />
          Back to Dashboard
        </Link>

        <div className="review-page-header">

          <div>
            <span className="eyebrow"><span className="status-dot" /> Review complete</span>
            <div className="review-title-row">
              <GitPullRequest size={24} />

              <h1>
                #{displayedReview.id} {displayedReview.title || "Pull request review"}
              </h1>
            </div>

            <p>
              {displayedReview.repositoryName || displayedReview.repository}
              {displayedReview.branchName || displayedReview.branch
                ? ` • ${displayedReview.branchName || displayedReview.branch}`
                : ""}
              {displayedReview.owner || displayedReview.author
                ? ` • ${displayedReview.owner || displayedReview.author}`
                : ""}
            </p>
          </div>

          <div className="complete-badge">
            <span className="status-dot" /> Complete
          </div>

        </div>

        <div className="change-summary">

          <div>
            <FileCode2 size={18} />
            <span>{displayedReview.changedFiles || "--"} files changed</span>
          </div>

          <div className="addition">
            <Plus size={18} />
            <span>{displayedReview.additions || "--"}</span>
          </div>

          <div className="deletion">
            <Minus size={18} />
            <span>{displayedReview.deletions || "--"}</span>
          </div>

        </div>

        <section className="review-summary-card">
          <h2>AI Review Summary</h2>

            <div className="review-markdown">
              {renderReviewText(displayedReview.reviewText || displayedReview.summary)}
            </div>
        </section>

        <div className="review-grid">

          <section className="code-card">

            <div className="code-header">
              <div>
                <h2>Code</h2>
                <span>AuthService.java</span>
              </div>
            </div>

            <pre>
              <code>{displayedReview.code || "No source excerpt was returned by the backend."}</code>
            </pre>

          </section>

          <section className="findings-card">

            <div className="findings-header">
              <h2>AI Findings</h2>

              <span>
                {displayedReview.findings?.length || 0} issues
              </span>
            </div>

            <div className="findings-list">

              {(displayedReview.findings || []).map((finding) => (
                <div
                  className="finding-item"
                  key={finding.id}
                >

                  <div className="finding-top">

                    <span
                      className={`severity-label ${finding.severity.toLowerCase()}`}
                    >
                      {finding.severity}
                    </span>

                    <span className="finding-type">
                      {finding.type}
                    </span>

                  </div>

                  <h3>{finding.title}</h3>

                  <div className="finding-location">
                    {finding.file} • Line {finding.line}
                  </div>

                  <p>{finding.description}</p>

                </div>
              ))}

            </div>

          </section>

        </div>

      </main>

    </div>
  );
}

export default ReviewDetails;