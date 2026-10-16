import { ChevronDown, ChevronUp, FileText, FolderGit2, LoaderCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { getReviews } from "../services/api";
import ReviewContent from "../components/ReviewContent";
import { EmptyState } from "./Dashboard";
import { orderReviews, parseReviewText, repositoryName, reviewDate } from "../utils/reviewData";

function HistoryCard({ review, expanded, onToggle }) {
  const parsedReview = parseReviewText(review.reviewText);
  const date = reviewDate(review);

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
  const [expandedId, setExpandedId] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getReviews()
      .then((data) => setReviews(orderReviews(data)))
      .catch(() => setError("Unable to load review history right now."))
      .finally(() => setIsLoading(false));
  }, []);

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
      {error && <div className="page-alert">{error}</div>}
      {isLoading ? (
        <div className="loading-page"><LoaderCircle className="spin" size={22} /> Loading review history...</div>
      ) : reviews.length === 0 ? (
        <EmptyState />
      ) : (
        <section className="history-list">
          {reviews.map((review) => (
            <HistoryCard
              key={review.id}
              review={review}
              expanded={expandedId === review.id}
              onToggle={() => setExpandedId(expandedId === review.id ? null : review.id)}
            />
          ))}
        </section>
      )}
    </main>
  );
}
