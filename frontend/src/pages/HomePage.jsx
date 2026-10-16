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

import { createReview } from "../services/api";

import {
  parseGitHubPullRequestUrl
} from "../utils/parseGitHubUrl";
import { parseReviewText } from "../utils/reviewData";
import ReviewContent from "../components/ReviewContent";


function requestErrorMessage(error) {
  if (!error.response) {
    return "Unable to connect to the review server. Check that the backend is running and try again.";
  }

  const responseData = error.response.data;
  if (typeof responseData === "string" && responseData.trim()) {
    return responseData;
  }

  if (responseData && typeof responseData === "object") {
    const message = responseData.message || responseData.error;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  return `Unable to analyze this pull request (HTTP ${error.response.status}).`;
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
    ? parseReviewText(review.reviewText)
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
      window.dispatchEvent(new Event("reviews:updated"));


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
