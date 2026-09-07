import { useState } from "react";
import { Link } from "react-router-dom";

import {
  ArrowLeft,
  GitPullRequest,
  AlertTriangle,
  Search,
} from "lucide-react";

import Sidebar from "../components/Sidebar";

import {
  analyzePullRequest,
} from "../services/api";


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

              <div className="pr-preview-content">

                <h2>
                  Pull Request Detected
                </h2>


                <div className="pr-info-row">

                  <span>
                    Owner
                  </span>

                  <strong>
                    {prInfo.owner}
                  </strong>

                </div>


                <div className="pr-info-row">

                  <span>
                    Repository
                  </span>

                  <strong>
                    {prInfo.repository}
                  </strong>

                </div>


                <div className="pr-info-row">

                  <span>
                    Pull Request
                  </span>

                  <strong>
                    #{prInfo.pullRequestNumber}
                  </strong>

                </div>


                <div className="analysis-next-step">

                  Spring Boot successfully received
                  and processed this Pull Request URL.

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