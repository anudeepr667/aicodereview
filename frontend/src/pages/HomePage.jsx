import ReactMarkdown from "react-markdown";
import {
  useEffect,
  useRef,
  useState
} from "react";

import {
  ArrowRight,
  Github,
  RotateCcw
} from "lucide-react";

import { Link } from "react-router-dom";
import {
  clearReviewRequestError,
  getReviewRequestState,
  submitReviewRequest,
  subscribeToReviewRequest
} from "../services/reviewRequest";
import {
  parseGitHubPullRequestUrl
} from "../utils/parseGitHubUrl";
import { parseReviewText } from "../utils/reviewData";
import ReviewContent from "../components/ReviewContent";
import ReviewShareButton from "../components/ReviewShareButton";


export default function HomePage() {

  const [
    pullRequestUrl,
    setPullRequestUrl
  ] = useState(() => getReviewRequestState().pullRequestUrl || "");

  const [
    requestState,
    setRequestState
  ] = useState(getReviewRequestState);

  const [inputError, setInputError] = useState("");
  const review = requestState.review;
  const isLoading = requestState.status === "processing";

  const structuredReview = review
    ? parseReviewText(review.reviewText)
    : null;
  const interruptedCurrentReview = requestState.status === "interrupted"
    && requestState.pullRequestUrl === pullRequestUrl;


  const autoReviewStarted = useRef(false);
  const reviewClickStartedAt = useRef(null);
  const measuredReviewId = useRef(null);

  useEffect(() => {
    const unsubscribe = subscribeToReviewRequest((nextState) => {
      setRequestState(nextState);
      if (nextState.pullRequestUrl) {
        setPullRequestUrl(nextState.pullRequestUrl);
      }
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    if (requestState.status === "failed" || requestState.status === "interrupted") {
      reviewClickStartedAt.current = null;
      return;
    }

    if (requestState.status !== "completed"
      || !review?.id
      || reviewClickStartedAt.current === null
      || measuredReviewId.current === review.id) {
      return;
    }

    measuredReviewId.current = review.id;
    console.info(
      "Review timing:",
      JSON.stringify({
        source: "browser",
        stage: "review_click_to_render",
        durationMs: Math.round(performance.now() - reviewClickStartedAt.current),
        outcome: "success",
        reviewId: review.id
      })
    );
    reviewClickStartedAt.current = null;
  }, [requestState.status, review]);

  async function reviewPullRequest(url, { automatic = false, force = false } = {}) {

    const parsed =
      parseGitHubPullRequestUrl(url);


    if (!parsed) {

      setInputError("Enter a valid GitHub pull request URL.");

      return;

    }

    const savedState = getReviewRequestState();
    if (automatic && savedState.pullRequestUrl === url && savedState.status !== "idle") {
      return;
    }
    if (!force
      && savedState.pullRequestUrl === url
      && savedState.status === "interrupted") {
      return;
    }

    setPullRequestUrl(url);
    setInputError("");

    try {
      await submitReviewRequest(url, {
        repositoryName: `${parsed.owner}/${parsed.repo}`,
        branchName: "main",
        pullRequestId: parsed.pullRequestId
      }, { allowInterruptedRetry: force });
    } catch {
      // The shared request store publishes a safe error state for the mounted page.
    }

  }


  async function handleSubmit(event) {

    event.preventDefault();
    reviewClickStartedAt.current = performance.now();

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

    reviewPullRequest(prUrl, { automatic: true });

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
              disabled={isLoading || interruptedCurrentReview}
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

              Analyzing pull request...

            </div>

          )
        }


        {
          (inputError
            || requestState.status === "failed"
            || interruptedCurrentReview) && (

            <div
              className="notice error"
              role="alert"
            >

              <span>
                {inputError || requestState.errorMessage}
              </span>

              {interruptedCurrentReview ? (
                <>
                  <Link to="/history" className="text-link">Check review history</Link>
                  <button
                    type="button"
                    onClick={() => reviewPullRequest(pullRequestUrl, { force: true })}
                  >
                    Start another review
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    setInputError("");
                    clearReviewRequestError();
                  }}
                >
                  <RotateCcw size={16} />
                  Try Again
                </button>
              )}

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

            <ReviewShareButton review={review} />

            <div className="review-text">
              {structuredReview ? (
                <ReviewContent review={structuredReview} />
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
