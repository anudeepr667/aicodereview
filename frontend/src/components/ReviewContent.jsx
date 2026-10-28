import ReactMarkdown from "react-markdown";
import { ShieldAlert } from "lucide-react";
import { buildCategoryReviews, reviewTextValue } from "../utils/reviewData";

export default function ReviewContent({ review }) {
  const score = Number.isFinite(Number(review.overallScore))
    ? Math.min(100, Math.max(0, Number(review.overallScore)))
    : null;
  const categories = buildCategoryReviews(review.categoryReviews);

  return (
    <div className="structured-review">
      <div className="score-panel">
        <div>
          <p className="section-kicker">Overall assessment</p>
          <p className="score-note">A directional signal for this change set</p>
        </div>
        <div className="score-value">
          <strong>{score === null ? "—" : score}</strong>
          <span>/100</span>
        </div>
        <div className="score-track" aria-label={score === null ? "Score unavailable" : `Overall score: ${score} out of 100`}>
          <span style={{ width: `${score ?? 0}%` }} />
        </div>
      </div>

      <section className="summary-panel">
        <p className="section-kicker">Summary</p>
        <ReactMarkdown>
          {reviewTextValue(review.summary, "No summary was provided for this review.")}
        </ReactMarkdown>
      </section>

      <section className="category-list">
        <div className="review-section-heading">
          <p className="section-kicker">Review breakdown</p>
          <span>Five review lenses</span>
        </div>
        {categories.map(({ category, review: categoryReview }, index) => (
          <article className="category-section" key={category}>
            <div className="category-number">{String(index + 1).padStart(2, "0")}</div>
            <div>
              <h3>{category}</h3>
              <ReactMarkdown>
                {reviewTextValue(categoryReview, "No category-specific explanation was provided.")}
              </ReactMarkdown>
            </div>
          </article>
        ))}
      </section>

      <section className="findings-section">
        <div className="review-section-heading">
          <p className="section-kicker">Actionable findings</p>
          <span>{review.findings.length} {review.findings.length === 1 ? "issue" : "issues"}</span>
        </div>
        {review.findings.length ? review.findings.map((finding, index) => {
          const severity = reviewTextValue(finding?.severity, "Low");
          return (
            <article className="finding-card" key={`${finding?.file || "finding"}-${finding?.line || index}-${index}`}>
              <div className="finding-topline">
                <span className={`severity severity-${severity.toLowerCase()}`}>
                  <ShieldAlert size={14} />
                  {severity}
                </span>
                <span className="finding-category">{reviewTextValue(finding?.category, "Uncategorized")}</span>
                {finding?.file && (
                  <code>{finding.file}{finding.line !== undefined && finding.line !== null ? `:${finding.line}` : ""}</code>
                )}
              </div>
              <h3>{reviewTextValue(finding?.problem, "No problem description was provided.")}</h3>
              <ReactMarkdown>
                {reviewTextValue(finding?.explanation, "No further explanation was provided.")}
              </ReactMarkdown>
              <div className="suggested-fix">
                <span>Suggested fix</span>
                <ReactMarkdown>
                  {reviewTextValue(finding?.suggestedFix, "No suggested fix was provided.")}
                </ReactMarkdown>
              </div>
            </article>
          );
        }) : (
          <p className="empty-review-message">No actionable issues were identified in the supplied changes.</p>
        )}
      </section>

      <section className="positives-section">
        <div className="review-section-heading">
          <p className="section-kicker">Positive aspects</p>
        </div>
        {review.positiveAspects.length ? (
          <ul>
            {review.positiveAspects.map((aspect, index) => (
              <li key={`${aspect}-${index}`}>{reviewTextValue(aspect, "No specific positive aspect was identified.")}</li>
            ))}
          </ul>
        ) : (
          <p className="empty-review-message">No specific positive aspects were identified.</p>
        )}
      </section>
    </div>
  );
}
