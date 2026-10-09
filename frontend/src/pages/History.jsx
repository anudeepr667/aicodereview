import { ChevronDown, ChevronUp, FileText, LoaderCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { getReviews } from "../services/api";
import { orderReviews, repositoryName } from "../utils/reviewData";
import { EmptyState } from "./Dashboard";

export default function History() {
  const [reviews, setReviews] = useState([]);
  const [expandedId, setExpandedId] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => { getReviews().then((data) => setReviews(orderReviews(data))).catch(() => setError("Unable to load review history right now.")).finally(() => setIsLoading(false)); }, []);
  return <main className="page-shell"><div className="page-heading"><div><p className="section-kicker"><FileText size={14} /> Archive</p><h1>Review history</h1><p className="page-subtitle">Browse every AI review returned by your backend.</p></div><span className="count-badge">{reviews.length} {reviews.length === 1 ? "review" : "reviews"}</span></div>{error && <div className="page-alert">{error}</div>}{isLoading ? <div className="loading-page"><LoaderCircle className="spin" size={22} /> Loading review history...</div> : reviews.length === 0 ? <EmptyState /> : <section className="history-list">{reviews.map((review) => { const isExpanded = expandedId === review.id; return <article className={`history-card${isExpanded ? " expanded" : ""}`} key={review.id}><button className="history-toggle" onClick={() => setExpandedId(isExpanded ? null : review.id)} aria-expanded={isExpanded}><div className="history-title"><span className="repo-icon"><FileText size={16} /></span><div><strong>{repositoryName(review)}</strong><span>Pull request #{review.pullRequestId} <i /> Review #{review.id}</span></div></div><span className="success-pill"><span className="status-dot" /> Reviewed</span>{isExpanded ? <ChevronUp size={19} /> : <ChevronDown size={19} />}</button>{isExpanded && <div className="history-body"><p>AI review</p><pre className="review-text">{review.reviewText || "No review text was returned."}</pre></div>}</article>; })}</section>}</main>;
}