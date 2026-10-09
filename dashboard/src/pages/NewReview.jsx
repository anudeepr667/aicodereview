import { useState } from "react";
import { Link } from "react-router-dom";

import {
  ArrowLeft,
  ArrowRight,
  Check,
  GitPullRequest,
  AlertTriangle,
  Search,
} from "lucide-react";

import Sidebar from "../components/Sidebar";

import {
  analyzePullRequest,
} from "../services/api";

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
          {lines.map((line) => (
            <li key={line}>{line.replace(/^[-*]\s+/, "")}</li>
          ))}
        </ul>
      );
    }

    if (lines.every((line) => /^\d+[.)]\s+/.test(line))) {
      return (
        <ol key={index}>
          {lines.map((line) => (
            <li key={line}>{line.replace(/^\d+[.)]\s+/, "")}</li>
          ))}
        </ol>
      );
    }

    return <p key={index}>{lines.join(" ")}</p>;
  });
}


function NewReview() {
  const [prUrl, setPrUrl] = useState("");

  const [error, setError] = useState("");

  const [prInfo, setPrInfo] = useState(null);

  const [loading, setLoading] = useState(false);


  async function handleAnalyze() {

    if (!prUrl.trim()) {
      setError(
        "Please enter a GitHub Pull Request URL."
      );

      return;
    }


    try {

      setLoading(true);

      setError("");

      setPrInfo(null);


      const result =
        await analyzePullRequest(prUrl);


      setPrInfo(result);

    } catch (err) {

      setError(err.message);

    } finally {

      setLoading(false);

    }
  }


  return (
    <div className="app-layout">

      <Sidebar />


      <main className="main-content">

        <Link
          to="/"
          className="back-link"
        >

          <ArrowLeft size={18} />

          Back to Dashboard

        </Link>


        <div className="pr-analysis-header">

          <div className="pr-analysis-title">

            <GitPullRequest size={30} />

            <div>

              <h1>
                Analyze Pull Request
              </h1>

              <p>
                Paste a GitHub Pull Request URL and
                CodeReview AI will automatically fetch
                and analyze the changed code.
              </p>

            </div>

          </div>

        </div>


        <div className="pr-analysis-container">


          <section className="pr-input-card">

            <div className="github-label">

              <GitPullRequest size={22} />

              <span>
                GitHub Pull Request
              </span>

            </div>


            <label htmlFor="pr-url">

              Pull Request URL

            </label>


            <input
              id="pr-url"
              type="text"
              className="pr-url-input"
              placeholder="https://github.com/username/repository/pull/24"
              value={prUrl}

              onChange={(event) =>
                setPrUrl(event.target.value)
              }
            />


            {error && (

              <div className="form-error">

                <AlertTriangle size={17} />

                {error}

              </div>

            )}


            <button
              type="button"
              className="analyze-pr-btn"
              onClick={handleAnalyze}
              disabled={loading}
            >

              <Search size={18} />

              {loading
                ? "Analyzing..."
                : "Analyze Pull Request"}

            </button>

          </section>


          <section className="pr-preview-card">

            {loading ? (

              <div className="empty-pr-preview">

                <div className="loading-circle">
                </div>

                <h2>
                  Contacting Backend...
                </h2>

                <p>
                  Validating your GitHub Pull Request.
                </p>

              </div>

            ) : !prInfo ? (

              <div className="empty-pr-preview">

                <GitPullRequest size={42} />

                <h2>
                  Ready to Review
                </h2>

                <p>
                  Enter a GitHub Pull Request URL.
                  Everything else will be handled
                  automatically.
                </p>

              </div>

            ) : (

              <div className="completed-review">

                <div className="completed-review-header">
                  <div>
                    <span className="eyebrow">
                      <span className="status-dot" /> Review complete
                    </span>
                    <h2>REVIEW COMPLETE</h2>
                  </div>

                  <span className="complete-badge">
                    <Check size={14} /> Complete
                  </span>
                </div>


                <div className="pr-info-row">

                  <span>Repository</span>

                  <strong>
                    {prInfo.repository || prInfo.repositoryName || "-"}
                  </strong>

                </div>


                <div className="pr-info-row">

                  <span>Pull Request</span>

                  <strong>
                    #{prInfo.pullRequestNumber || prInfo.pullRequestId || "-"}
                  </strong>

                </div>


                <div className="pr-info-row">

                  <span>Review ID</span>

                  <strong>
                    #{prInfo.id || "-"}
                  </strong>

                </div>

                <section className="immediate-review-result">
                  <div className="immediate-review-title">
                    <h3>AI Review</h3>
                    {prInfo.branchName && <span>Branch: {prInfo.branchName}</span>}
                  </div>
                  <div className="review-markdown">
                    {renderReviewText(prInfo.reviewText)}
                  </div>
                </section>

                <div className="completed-review-actions">
                  <span>Saved to review history</span>
                  {prInfo.id && (
                    <Link to={`/review/${prInfo.id}`} className="details-link">
                      View full details <ArrowRight size={15} />
                    </Link>
                  )}
                </div>

              </div>

            )}

          </section>


        </div>

      </main>

    </div>
  );
}


export default NewReview;