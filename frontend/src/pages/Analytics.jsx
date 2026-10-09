import { createElement, useEffect, useState } from "react";
import { Activity, BarChart3, FolderGit2, LoaderCircle, TrendingUp } from "lucide-react";
import { getReviews } from "../services/api";
import { orderReviews, repositoryCounts, repositoryName } from "../utils/reviewData";
import { EmptyState } from "./Dashboard";

export default function Analytics() {
  const [reviews, setReviews] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => { getReviews().then((data) => setReviews(orderReviews(data))).catch(() => setError("Unable to load analytics right now.")).finally(() => setIsLoading(false)); }, []);
  const counts = repositoryCounts(reviews);
  const repositories = Object.entries(counts).sort(([, left], [, right]) => right - left);
  const maxCount = repositories[0]?.[1] || 1;
  const mostReviewed = repositories[0];
  return <main className="page-shell"><div className="page-heading"><div><p className="section-kicker"><BarChart3 size={14} /> Insights</p><h1>Review analytics</h1><p className="page-subtitle">Understand where your code review activity is concentrated.</p></div></div>{error && <div className="page-alert">{error}</div>}{isLoading ? <div className="loading-page"><LoaderCircle className="spin" size={22} /> Loading analytics...</div> : reviews.length === 0 ? <EmptyState /> : <><section className="stat-grid analytics-stats"><Stat icon={Activity} label="Total Reviews" value={reviews.length} /><Stat icon={FolderGit2} label="Unique Repositories" value={repositories.length} /><Stat icon={TrendingUp} label="Most Reviewed Repository" value={mostReviewed[0]} detail={`${mostReviewed[1]} reviews`} /></section><section className="analytics-grid"><div className="panel"><div className="section-heading"><div><p className="section-kicker">Distribution</p><h2>Reviews by repository</h2></div></div><div className="bar-chart">{repositories.map(([name, count]) => <div className="bar-row" key={name}><div className="bar-label"><span>{name}</span><strong>{count}</strong></div><div className="bar-track"><span style={{ width: `${(count / maxCount) * 100}%` }} /></div></div>)}</div></div><div className="panel activity-panel"><div className="section-heading"><div><p className="section-kicker">Latest</p><h2>Recent activity</h2></div></div><div className="activity-list">{reviews.slice(0, 5).map((review) => <div className="activity-item" key={review.id}><span className="activity-dot" /><div><strong>{repositoryName(review)}</strong><span>PR #{review.pullRequestId} reviewed</span></div></div>)}</div></div></section></>}</main>;
}

function Stat({ icon, label, value, detail }) { return <article className="stat-card"><div className="stat-icon">{createElement(icon, { size: 18 })}</div><p>{label}</p><strong className="stat-value-text">{value}</strong><span>{detail || "Based on stored reviews"}</span></article>; }