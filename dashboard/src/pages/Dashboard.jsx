import { useEffect, useState } from "react";
import { ArrowRight, Bot, GitPullRequest, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import Topbar from "../components/Topbar";
import { analyzePullRequest, getReviews } from "../services/api";

function renderReviewText(text) {
  if (!text) {
    return <p>No AI review text was returned.</p>;
  }

  const blocks = text.split(/\n\s*\n/).filter(Boolean);

  return blocks.map((block, index) => {
    const lines = block.split("\n").map((line) => line.trim()).filter(Boolean);
    const heading = lines[0]?.match(/^#{1,4}\s+(.+)/);

    if (heading) {
      return <h3 key={index}>{heading[1]}</h3>;
    }

    if (lines.every((line) => /^[-*]\s+/.test(line))) {
      return (
        <ul key={index}>
          {lines.map((line) => <li key={line}>{line.replace(/^[-*]\s+/, "")}</li>)}
        </ul>
      );
    }

    if (lines.every((line) => /^\d+[.)]\s+/.test(line))) {
      return (
        <ol key={index}>
          {lines.map((line) => <li key={line}>{line.replace(/^\d+[.)]\s+/, "")}</li>)}
        </ol>
      );
    }

    return <p key={index}>{lines.join(" ")}</p>;
  });
}


function Dashboard() {
  const [pullRequestUrl, setPullRequestUrl] = useState("");
  const [reviews, setReviews] = useState([]);
  const [latestReview, setLatestReview] = useState(null);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    async function loadReviews() {
      try {
        setReviews(await getReviews());
      } catch {
        setReviews([]);
      }
    }

    loadReviews();
  }, []);

  async function handleSubmit(event) {
    event.preventDefault();

    if (!pullRequestUrl.trim()) {
      setError("Enter a GitHub pull request URL.");
      return;
    }

    try {
      setError("");
      setIsLoading(true);
      const review = await analyzePullRequest(pullRequestUrl);
      setLatestReview(review);
      setReviews((currentReviews) => [review, ...currentReviews]);
      setPullRequestUrl("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="app-layout">

      <Sidebar />

      <main className="main-content">
        <Topbar />

        <section className="dashboard-hero">
          <div className="hero-mark"><Bot size={18} /></div>
          <span className="eyebrow"><span className="status-dot" /> AI-powered pull request analysis</span>
          <h1>Review Pull Requests<br />Before They Become Problems.</h1>
          <p>Paste a GitHub pull request and receive a focused AI review covering bugs, security, performance, readability and best practices.</p>

          <form className="dashboard-review-form" onSubmit={handleSubmit} noValidate>
            <label htmlFor="dashboard-pr-url">Paste GitHub Pull Request URL</label>
            <div className="dashboard-input-row">
              <div className="dashboard-input-wrap">
                <GitPullRequest size={18} aria-hidden="true" />
                <input
                  id="dashboard-pr-url"
                  type="url"
                  value={pullRequestUrl}
                  onChange={(event) => setPullRequestUrl(event.target.value)}
                  placeholder="https://github.com/owner/repository/pull/24"
                  disabled={isLoading}
                />
              </div>
              <button type="submit" disabled={isLoading}>
                {isLoading ? "Reviewing..." : "Review Pull Request"}
                <ArrowRight size={17} />
              </button>
            </div>
          </form>

          {error && (
            <div className="dashboard-error" role="alert">
              <span>{error}</span>
              <button type="button" onClick={() => setError("")} aria-label="Dismiss error">
                <RotateCcw size={15} />
              </button>
            </div>
          )}
        </section>

        {latestReview && (
          <section className="immediate-review-card">
            <div className="completed-review-header">
              <div>
                <span className="eyebrow"><span className="status-dot" /> Review complete</span>
                <h2>REVIEW COMPLETE</h2>
              </div>
              <span className="complete-badge"><span className="status-dot" /> Complete</span>
            </div>

            <div className="immediate-review-meta">
              <div><span>Repository</span><strong>{latestReview.repositoryName || "-"}</strong></div>
              <div><span>Pull Request</span><strong>#{latestReview.pullRequestId || "-"}</strong></div>
              <div><span>Review ID</span><strong>#{latestReview.id || "-"}</strong></div>
            </div>

            <div className="immediate-review-result">
              <div className="immediate-review-title"><h3>AI Review</h3></div>
              <div className="review-markdown">{renderReviewText(latestReview.reviewText)}</div>
            </div>

            {latestReview.id && (
              <Link to={`/review/${latestReview.id}`} className="details-link">
                View full details <ArrowRight size={15} />
              </Link>
            )}
          </section>
        )}

        <section className="recent-reviews-section">
          <div className="section-heading">
            <div>
              <span className="section-kicker">Review history</span>
              <h2>Recent Reviews</h2>
            </div>
            <Link to="/new-review">Open full analyzer <ArrowRight size={15} /></Link>
          </div>

          {reviews.length === 0 ? (
            <div className="empty-reviews">
              <GitPullRequest size={19} />
              <span>Your completed reviews will appear here.</span>
            </div>
          ) : (
            <div className="recent-review-list">
              {reviews.slice(0, 5).map((review) => (
                <Link className="recent-review-row" to={`/review/${review.id}`} key={review.id}>
                  <span className="review-row-icon"><GitPullRequest size={16} /></span>
                  <span className="review-row-main">
                    <strong>{review.repositoryName || "Repository review"}</strong>
                    <small>Pull request #{review.pullRequestId || "-"}</small>
                  </span>
                  <span className="review-row-status"><span className="status-dot" /> Complete</span>
                  <ArrowRight size={16} />
                </Link>
              ))}
            </div>
          )}
        </section>

      </main>

    </div>
  );
}

export default Dashboard;