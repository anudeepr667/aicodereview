import { createElement, useEffect, useMemo, useState } from "react";
import { BarChart3, CircleAlert, FileSearch, FolderGit2, LoaderCircle, ShieldAlert, Star, Target } from "lucide-react";
import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getReviews } from "../services/api";
import { orderReviews, parseReviewText, repositoryName } from "../utils/reviewData";
import { EmptyState } from "./Dashboard";

const SEVERITIES = ["Critical", "High", "Medium", "Low"];
const SEVERITY_COLORS = { Critical: "#f07c82", High: "#e58b78", Medium: "#e0ae62", Low: "#65c99c" };
const SCORE_RANGES = [{ label: "0–49", min: 0, max: 49 }, { label: "50–69", min: 50, max: 69 }, { label: "70–84", min: 70, max: 84 }, { label: "85–100", min: 85, max: 100 }];

function validScore(review) { const score = Number(review?.overallScore); return Number.isFinite(score) ? Math.min(100, Math.max(0, score)) : null; }

function uniqueFindings(review) {
  const seen = new Set();
  return (Array.isArray(review?.findings) ? review.findings.filter(Boolean) : []).filter((finding, index) => {
    const key = [finding.file, finding.line, finding.problem, finding.category].map((value) => String(value ?? "").trim().toLowerCase()).join("|");
    const uniqueKey = key === "|||" ? `finding-${index}` : key;
    if (seen.has(uniqueKey)) return false;
    seen.add(uniqueKey);
    return true;
  });
}

function metricData(reviews) {
  const structured = reviews.map((record) => ({ record, review: parseReviewText(record.reviewText) })).filter(({ review }) => review);
  const scores = structured.map(({ review }) => validScore(review)).filter((score) => score !== null);
  const findings = structured.flatMap(({ review }) => uniqueFindings(review));
  const severity = Object.fromEntries(SEVERITIES.map((name) => [name, 0]));
  findings.forEach((finding) => {
    const matchedSeverity = SEVERITIES.find((name) => name.toLowerCase() === String(finding.severity || "").trim().toLowerCase());
    if (matchedSeverity) severity[matchedSeverity] += 1;
  });
  return { scores, findings, severity, averageScore: scores.length ? Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length) : null };
}

function Stat({ icon: Icon, label, value, detail, tone }) { return <article className={`analytics-kpi analytics-kpi-${tone}`}><div className="analytics-kpi-icon" aria-hidden="true">{createElement(Icon, { size: 18 })}</div><div><p>{label}</p><strong>{value}</strong><span>{detail}</span></div></article>; }
function EmptyChart({ children }) { return <div className="analytics-empty"><FileSearch size={20} /><p>{children}</p></div>; }
function ChartTooltip({ active, payload, label, valueLabel }) { return active && payload?.length ? <div className="chart-tooltip"><strong>{label}</strong><span>{payload[0].value} {valueLabel}</span></div> : null; }

function RankingChart({ items, emptyText, heightClass = "chart-box-category", labelWidth = 150 }) {
  if (!items.length) return <EmptyChart>{emptyText}</EmptyChart>;
  const data = items.slice(0, 8).map(([name, value]) => ({ name, value }));
  return <div className={`chart-box ${heightClass}`}><ResponsiveContainer width="100%" height="100%"><BarChart layout="vertical" data={data} margin={{ top: 4, right: 30, left: 10, bottom: 4 }}><CartesianGrid stroke="#25334a" strokeDasharray="3 3" horizontal={false} /><XAxis type="number" allowDecimals={false} stroke="#718097" tick={{ fill: "#8b98ac", fontSize: 11 }} axisLine={{ stroke: "#2a3851" }} /><YAxis type="category" dataKey="name" width={labelWidth} stroke="#718097" tick={{ fill: "#c0c9d8", fontSize: 11 }} tickLine={false} axisLine={false} /><Tooltip content={<ChartTooltip valueLabel="reviews" />} /><Bar dataKey="value" name="Reviews" fill="#7186ff" radius={[0, 5, 5, 0]}><LabelList dataKey="value" position="right" fill="#e8edf7" fontSize={12} /></Bar></BarChart></ResponsiveContainer></div>;
}

function SeverityChart({ findings, severity }) {
  if (!findings.length) return <EmptyChart>No findings are available to chart.</EmptyChart>;
  const data = SEVERITIES.map((name) => ({ name, value: severity[name] }));
  return <div className="chart-box chart-box-bar"><ResponsiveContainer width="100%" height="100%"><BarChart layout="vertical" data={data} margin={{ top: 4, right: 30, left: 12, bottom: 4 }}><CartesianGrid stroke="#25334a" strokeDasharray="3 3" horizontal={false} /><XAxis type="number" allowDecimals={false} stroke="#718097" tick={{ fill: "#8b98ac", fontSize: 11 }} axisLine={{ stroke: "#2a3851" }} /><YAxis type="category" dataKey="name" width={66} stroke="#718097" tick={{ fill: "#c0c9d8", fontSize: 12 }} tickLine={false} axisLine={false} /><Tooltip content={<ChartTooltip valueLabel="findings" />} /><Bar dataKey="value" name="Findings" radius={[0, 5, 5, 0]}><LabelList dataKey="value" position="right" fill="#e8edf7" fontSize={12} />{data.map((item) => <Cell key={item.name} fill={SEVERITY_COLORS[item.name]} />)}</Bar></BarChart></ResponsiveContainer></div>;
}

function ScoreChart({ scores }) {
  if (!scores.length) return <EmptyChart>No valid review scores are available yet.</EmptyChart>;
  const data = SCORE_RANGES.map((range) => ({ name: range.label, value: scores.filter((score) => score >= range.min && score <= range.max).length }));
  return <div className="chart-box chart-box-column"><ResponsiveContainer width="100%" height="100%"><BarChart data={data} margin={{ top: 14, right: 12, left: -18, bottom: 5 }}><CartesianGrid stroke="#25334a" strokeDasharray="3 3" vertical={false} /><XAxis dataKey="name" stroke="#718097" tick={{ fill: "#c0c9d8", fontSize: 11 }} tickLine={false} axisLine={{ stroke: "#2a3851" }} /><YAxis allowDecimals={false} stroke="#718097" tick={{ fill: "#8b98ac", fontSize: 11 }} tickLine={false} axisLine={false} /><Tooltip content={<ChartTooltip valueLabel="reviews" />} /><Bar dataKey="value" name="Reviews" fill="#8493ff" radius={[5, 5, 0, 0]}><LabelList dataKey="value" position="top" fill="#e8edf7" fontSize={12} /></Bar></BarChart></ResponsiveContainer></div>;
}

function RepositoryChart({ reviews }) {
  const counts = reviews.reduce((result, review) => { const name = repositoryName(review); result[name] = (result[name] || 0) + 1; return result; }, {});
  return <section className="analytics-panel"><div className="analytics-panel-heading"><div><p className="section-kicker"><FolderGit2 size={14} /> Repository focus</p><h2>Reviews by repository</h2></div><span className="analytics-caption">{Object.keys(counts).length} total</span></div><RankingChart items={Object.entries(counts).sort(([, left], [, right]) => right - left)} heightClass="chart-box-repository" labelWidth={150} emptyText="No repository data is available yet." /></section>;
}

export default function Analytics() {
  const [reviews, setReviews] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    const loadReviews = () => { setIsLoading(true); getReviews().then((data) => { if (active) { setReviews(orderReviews(data)); setError(""); } }).catch(() => { if (active) setError("Unable to load analytics right now."); }).finally(() => { if (active) setIsLoading(false); }); };
    loadReviews();
    window.addEventListener("reviews:updated", loadReviews);
    return () => { active = false; window.removeEventListener("reviews:updated", loadReviews); };
  }, []);
  const metrics = useMemo(() => metricData(reviews), [reviews]);
  const repositories = useMemo(() => new Set(reviews.map(repositoryName)).size, [reviews]);
  if (isLoading) return <main className="page-shell analytics-page"><div className="loading-page"><LoaderCircle className="spin" size={22} /> Loading analytics...</div></main>;
  if (error) return <main className="page-shell analytics-page"><div className="page-alert">{error}</div></main>;
  if (!reviews.length) return <main className="page-shell analytics-page"><EmptyState /></main>;
  return <main className="page-shell analytics-page"><div className="page-heading analytics-heading"><div><p className="section-kicker"><BarChart3 size={14} /> Intelligence</p><h1>Code Review Analytics</h1><p className="page-subtitle">A focused view of review quality, repositories, findings, and stored scores.</p></div></div><section className="analytics-kpi-grid" aria-label="Analytics overview"><Stat icon={BarChart3} label="Total Reviews" value={reviews.length} detail="Stored analyses" tone="blue" /><Stat icon={FolderGit2} label="Repositories Reviewed" value={repositories} detail="Unique repositories" tone="teal" /><Stat icon={Target} label="Average Review Score" value={metrics.averageScore === null ? "—" : `${metrics.averageScore}/100`} detail={metrics.scores.length ? `${metrics.scores.length} valid scores` : "No valid scores"} tone="violet" /><Stat icon={CircleAlert} label="Total Findings" value={metrics.findings.length} detail="Distinct parsed findings" tone="amber" /></section><div className="analytics-main-grid"><RepositoryChart reviews={reviews} /><section className="analytics-panel"><div className="analytics-panel-heading"><div><p className="section-kicker"><ShieldAlert size={14} /> Severity</p><h2>Findings by severity</h2></div><span className="analytics-caption">{metrics.findings.length} total</span></div><SeverityChart findings={metrics.findings} severity={metrics.severity} /></section></div><section className="analytics-panel analytics-score-panel"><div className="analytics-panel-heading"><div><p className="section-kicker"><Star size={14} /> Score profile</p><h2>Review score analysis</h2></div></div><ScoreChart scores={metrics.scores} /><p className="chart-note">Scores are indicative AI assessments, not guarantees of code quality.</p></section></main>;
}
