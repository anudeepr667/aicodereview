import { createElement, useEffect, useState } from "react";
import { BookOpen, ChevronRight, FolderGit2, GitPullRequest, LoaderCircle, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import { getReviews } from "../services/api";
import { orderReviews, repositoryName } from "../utils/reviewData";

export default function Dashboard() {
  const [reviews, setReviews] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => { getReviews().then((data) => setReviews(orderReviews(data))).catch(() => setError("Unable to load review activity right now.")).finally(() => setIsLoading(false)); }, []);
  const uniqueRepositories = new Set(reviews.map(repositoryName)).size;
  const latest = reviews[0];

  return <main className="page-shell"><div className="page-heading"><div><p className="section-kicker"><span className="status-dot" /> Workspace overview</p><h1>Review dashboard</h1><p className="page-subtitle">A clear view of the pull requests your team has analyzed.</p></div><Link className="secondary-button" to="/"><GitPullRequest size={16} /> Review a PR</Link></div>{error && <div className="page-alert">{error}<button onClick={() => window.location.reload()} type="button"><RefreshCw size={15} /> Retry</button></div>}{isLoading ? <LoadingState /> : <><section className="stat-grid" aria-label="Review statistics"><StatCard icon={BookOpen} label="Total Reviews" value={reviews.length} detail="Stored AI analyses" /><StatCard icon={FolderGit2} label="Unique Repositories" value={uniqueRepositories} detail="Across your workspace" /><StatCard icon={GitPullRequest} label="Latest Pull Request" value={latest ? `#${latest.pullRequestId}` : "--"} detail={latest ? repositoryName(latest) : "No reviews yet"} /></section><section className="content-section"><div className="section-heading"><div><p className="section-kicker">Activity</p><h2>Recent reviews</h2></div><Link to="/history" className="text-link">View all <ChevronRight size={15} /></Link></div>{reviews.length === 0 ? <EmptyState /> : <ReviewTable reviews={reviews.slice(0, 5)} />}</section></>}</main>;
}

function StatCard({ icon, label, value, detail }) { return <article className="stat-card"><div className="stat-icon">{createElement(icon, { size: 18 })}</div><p>{label}</p><strong>{value}</strong><span>{detail}</span></article>; }

export function ReviewTable({ reviews }) { return <div className="review-table">{reviews.map((review) => <div className="review-row" key={review.id}><div className="review-repo"><span className="repo-icon"><FolderGit2 size={16} /></span><div><strong>{repositoryName(review)}</strong><span>Pull request #{review.pullRequestId}</span></div></div><span className="review-id">Review #{review.id}</span><span className="success-pill"><span className="status-dot" /> Reviewed</span></div>)}</div>; }

export function EmptyState() { return <div className="empty-state"><div className="empty-icon"><BookOpen size={22} /></div><h3>No reviews yet</h3><p>Submit a GitHub pull request to start building your review history.</p><Link className="primary-button" to="/"><GitPullRequest size={16} /> Review your first PR</Link></div>; }

function LoadingState() { return <div className="loading-page"><LoaderCircle className="spin" size={22} /> Loading review activity...</div>; }