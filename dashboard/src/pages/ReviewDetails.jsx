import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  GitPullRequest,
  FileCode2,
  Plus,
  Minus,
} from "lucide-react";

import Sidebar from "../components/Sidebar";
import { reviewDetails } from "../data/mockData";

function ReviewDetails() {
  const { id } = useParams();

  const review = reviewDetails.find(
    (item) => item.id === Number(id)
  );

  if (!review) {
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
            <div className="review-title-row">
              <GitPullRequest size={24} />

              <h1>
                #{review.id} {review.title}
              </h1>
            </div>

            <p>
              {review.repository} • {review.branch} • {review.author}
            </p>
          </div>

          <div className="review-score">
            <span>Code Score</span>
            <strong>{review.score}</strong>
            <small>/100</small>
          </div>

        </div>

        <div className="change-summary">

          <div>
            <FileCode2 size={18} />
            <span>{review.changedFiles} files changed</span>
          </div>

          <div className="addition">
            <Plus size={18} />
            <span>{review.additions}</span>
          </div>

          <div className="deletion">
            <Minus size={18} />
            <span>{review.deletions}</span>
          </div>

        </div>

        <section className="review-summary-card">
          <h2>AI Review Summary</h2>

          <p>{review.summary}</p>
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
              <code>{review.code}</code>
            </pre>

          </section>

          <section className="findings-card">

            <div className="findings-header">
              <h2>AI Findings</h2>

              <span>
                {review.findings.length} issues
              </span>
            </div>

            <div className="findings-list">

              {review.findings.map((finding) => (
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