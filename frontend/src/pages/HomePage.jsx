import ReactMarkdown from "react-markdown";
import {
  useEffect,
  useRef,
  useState
} from "react";

import {
  ArrowRight,
  Github,
  RotateCcw,
  ShieldAlert
} from "lucide-react";

import { createReview } from "../services/api";

import {
  parseGitHubPullRequestUrl
} from "../utils/parseGitHubUrl";

const CATEGORY_ORDER = [
  "Bugs or Logical Errors",
  "Security Vulnerabilities",
  "Performance Improvements",
  "Code Quality and Readability",
  "Java and Framework Best Practices",
  "Positive Aspects"
];

function getStructuredReview(reviewText) {
  if (typeof reviewText !== "string" || !reviewText.trim()) {
    return null;
  }

  try {
    const parsed = JSON.parse(reviewText);

    if (
      !parsed ||
      typeof parsed !== "object" ||
      !Array.isArray(parsed.categoryReviews) ||
      !Array.isArray(parsed.findings) ||
      !Array.isArray(parsed.positiveAspects)
    ) {
      return null;
    }

    return parsed;
  } catch {
    return null;
  }
}

function reviewTextValue(value, fallback) {
  return typeof value === "string" && value.trim()
    ? value
    : fallback;
}

function StructuredReview({ review }) {
  const score = Number.isFinite(Number(review.overallScore))
    ? Math.min(100, Math.max(0, Number(review.overallScore)))
    : null;
  const categoryReviews = new Map(
    review.categoryReviews
      .filter((item) => item && typeof item.category === "string")
      .map((item) => [item.category, item.review])
  );

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
          <span>Six review lenses</span>
        </div>
        {CATEGORY_ORDER.map((category, index) => (
          <article className="category-section" key={category}>
            <div className="category-number">{String(index + 1).padStart(2, "0")}</div>
            <div>
              <h3>{category}</h3>
              <ReactMarkdown>
                {reviewTextValue(
                  categoryReviews.get(category),
                  "No category-specific explanation was provided."
                )}
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


function requestErrorMessage(error) {

  return !error.response
    ? "Unable to connect to the review server."
    : "Unable to analyze this pull request.";

}


export default function HomePage() {

  const [
    pullRequestUrl,
    setPullRequestUrl
  ] = useState("");

  const [
    review,
    setReview
  ] = useState(null);

  const [
    error,
    setError
  ] = useState("");

  const [
    isLoading,
    setIsLoading
  ] = useState(false);

  const structuredReview = review
    ? getStructuredReview(review.reviewText)
    : null;


  const autoReviewStarted = useRef(false);


  async function reviewPullRequest(url) {

    const parsed =
      parseGitHubPullRequestUrl(url);


    if (!parsed) {

      setReview(null);

      setError(
        "Enter a valid GitHub pull request URL."
      );

      return;

    }


    setPullRequestUrl(url);

    setError("");

    setReview(null);

    setIsLoading(true);


    try {

      const result =
        await createReview({

          repositoryName:
            `${parsed.owner}/${parsed.repo}`,

          branchName:
            "main",

          pullRequestId:
            parsed.pullRequestId

        });


      setReview(result);


    } catch (requestError) {

      console.error(
        "Review request failed:",
        requestError
      );

      setError(
        requestErrorMessage(requestError)
      );


    } finally {

      setIsLoading(false);

    }

  }


  async function handleSubmit(event) {

    event.preventDefault();

    await reviewPullRequest(
      pullRequestUrl
    );

  }


  useEffect(() => {

    if (autoReviewStarted.current) {
      return;
    }


    const params =
      new URLSearchParams(
        window.location.search
      );


    const prUrl =
      params.get("pr");

    const autoReview =
      params.get("autoReview");


    if (
      !prUrl ||
      autoReview !== "true"
    ) {
      return;
    }


    autoReviewStarted.current = true;


    setPullRequestUrl(prUrl);


    window.history.replaceState(
      {},
      "",
      window.location.pathname
    );


    reviewPullRequest(prUrl);

  }, []);


  return (

    <main className="shell review-page">


      <section className="hero">


        <div className="eyebrow">

          <span className="status-dot" />

          AI-powered pull request analysis

        </div>


        <h1>
          Review Pull Requests Before
          They Become Problems.
        </h1>


        <p className="lede">

          Paste a GitHub pull request and
          receive an AI-powered review covering
          bugs, security, performance,
          readability and best practices.

        </p>


        <form
          onSubmit={handleSubmit}
          noValidate
        >


          <label htmlFor="pr-url">

            Paste GitHub Pull Request URL

          </label>


          <div className="input-row">


            <div className="input-wrap">


              <Github
                aria-hidden="true"
                size={20}
              />


              <input
                id="pr-url"
                type="url"

                value={
                  pullRequestUrl
                }

                onChange={(event) =>
                  setPullRequestUrl(
                    event.target.value
                  )
                }

                placeholder=
                  "https://github.com/owner/repository/pull/123"

                disabled={
                  isLoading
                }
              />


            </div>


            <button
              type="submit"
              disabled={isLoading}
            >

              {
                isLoading
                  ? "Reviewing…"
                  : "Review Pull Request"
              }

              <ArrowRight
                size={18}
              />

            </button>


          </div>


        </form>


        {
          isLoading && (

            <div
              className="notice loading"
              role="status"
            >

              <span
                className="spinner"
              />

              Analyzing GitHub pull
              request with AI…

            </div>

          )
        }


        {
          error && (

            <div
              className="notice error"
              role="alert"
            >

              <span>
                {error}
              </span>


              <button
                type="button"
                onClick={() =>
                  setError("")
                }
              >

                <RotateCcw
                  size={16}
                />

                Try Again

              </button>


            </div>

          )
        }


      </section>


      {
        review && (

          <section className="review-card">


            <div className="review-top">


              <div>

                <p className="eyebrow">
                  Review complete
                </p>

                <h2>
                  AI Review
                </h2>

              </div>


              <span className="complete">

                <span
                  className="status-dot"
                />

                Complete

              </span>


            </div>


            <div className="metadata">


              <span>

                Repository

                <strong>
                  {
                    review.repositoryName
                  }
                </strong>

              </span>


              <span>

                Pull Request

                <strong>
                  #
                  {
                    review.pullRequestId
                  }
                </strong>

              </span>


              <span>

                Review ID

                <strong>
                  #
                  {
                    review.id
                  }
                </strong>

              </span>


            </div>


            <div className="review-text">
              {structuredReview ? (
                <StructuredReview review={structuredReview} />
              ) : (
                <ReactMarkdown>
                  {review.reviewText || "No review content was returned."}
                </ReactMarkdown>
              )}
</div>


          </section>

        )
      }


    </main>

  );

}