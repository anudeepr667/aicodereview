import { ChevronDown, ChevronUp, ExternalLink, FileText, FolderGit2, LoaderCircle, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { getReviews } from "../services/api";
import ReviewContent from "../components/ReviewContent";
import ReviewShareButton from "../components/ReviewShareButton";
import { EmptyState } from "./Dashboard";
import { githubPullRequestUrl, orderReviews, parseReviewText, repositoryName, reviewDate } from "../utils/reviewData";

function HistoryCard({ review, expanded, onToggle }) {
  const parsedReview = parseReviewText(review.reviewText);
  const date = reviewDate(review);
  const pullRequestUrl = githubPullRequestUrl(review);

  return (
    <article className={`history-card${expanded ? " expanded" : ""}`}>
      <div className="history-card-top">
        <div className="history-title">
          <span className="repo-icon"><FolderGit2 size={16} /></span>
          <div>
            <strong>{repositoryName(review)}</strong>
            <span>
              Pull request #{review.pullRequestId || "—"}
              <i />
              <span>Review #{review.id || "—"}</span>
              {date && <><i /><span>{date}</span></>}
            </span>
            <span className="history-card-links">
              {parsedReview
                ? `${parsedReview.findings.length} actionable ${parsedReview.findings.length === 1 ? "finding" : "findings"}`
                : "Finding count unavailable"}
              <i />
              <Link className="history-review-link" to={`/history?review=${encodeURIComponent(review.id)}`}>View detailed review</Link>
              {pullRequestUrl && (
                <>
                  <i />
                  <a className="history-review-link" href={pullRequestUrl} target="_blank" rel="noreferrer">
                    Original PR <ExternalLink size={13} />
                  </a>
                </>
              )}
            </span>
          </div>
        </div>
        {!expanded && parsedReview && <div className="history-card-score"><strong>{parsedReview.overallScore ?? "—"}</strong><span>/100</span></div>}
      </div>
      {expanded && (
        <div className="history-analysis">
          <div className="history-metadata metadata">
            <span>Repository<strong>{repositoryName(review)}</strong></span>
            <span>Pull Request<strong>#{review.pullRequestId || "—"}</strong></span>
            <span>Review ID<strong>#{review.id || "—"}</strong></span>
          </div>
          <ReviewShareButton review={review} />
          {parsedReview ? <ReviewContent review={parsedReview} /> : <p className="legacy-review">{review.reviewText ? "This review uses an older format and cannot be displayed as structured findings." : "No review content was returned."}</p>}
        </div>
      )}
      <button className="history-details-button" onClick={onToggle} aria-expanded={expanded} type="button">
        {expanded ? "Hide details" : "Show details"}
        {expanded ? <ChevronUp size={17} /> : <ChevronDown size={17} />}
      </button>
    </article>
  );
}

export default function History() {
  const [reviews, setReviews] = useState([]);
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedReviewId = searchParams.get("review");
  const [expandedId, setExpandedId] = useState(requestedReviewId);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const loadReviews = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      setReviews(orderReviews(await getReviews()));
    } catch {
      setError("Unable to load review history right now.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadReviews();
  }, [loadReviews]);

  useEffect(() => {
    setExpandedId(requestedReviewId);
  }, [requestedReviewId]);

  return (
    <main className="page-shell">
      <div className="page-heading">
        <div>
          <p className="section-kicker"><FileText size={14} /> Archive</p>
          <h1>Review history</h1>
          <p className="page-subtitle">Browse every AI review returned by your backend.</p>
        </div>
        <span className="count-badge">{reviews.length} {reviews.length === 1 ? "review" : "reviews"}</span>
      </div>
      {error && (
        <div className="page-alert" role="alert">
          {error}
          <button type="button" onClick={loadReviews}><RefreshCw size={15} /> Retry</button>
        </div>
      )}
      {isLoading ? (
        <div className="loading-page"><LoaderCircle className="spin" size={22} /> Loading review history...</div>
      ) : error && reviews.length === 0 ? null : reviews.length === 0 ? (
        <EmptyState />
      ) : (
        <section className="history-list">
          {reviews.map((review) => (
            <HistoryCard
              key={review.id}
              review={review}
              expanded={expandedId === String(review.id)}
              onToggle={() => {
                setExpandedId(expandedId === String(review.id) ? null : String(review.id));
                if (requestedReviewId) setSearchParams({}, { replace: true });
              }}
            />
          ))}
        </section>
      )}
    </main>
  );
}
