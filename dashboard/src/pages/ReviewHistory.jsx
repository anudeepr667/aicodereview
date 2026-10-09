import { useEffect, useState } from "react";
import { ArrowRight, GitPullRequest } from "lucide-react";
import { Link } from "react-router-dom";

import Sidebar from "../components/Sidebar";
import Topbar from "../components/Topbar";
import { getReviews } from "../services/api";

function ReviewHistory() {
  const [reviews, setReviews] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function loadReviews() {
      try {
        setReviews(await getReviews());
      } catch {
        setReviews([]);
      } finally {
        setIsLoading(false);
      }
    }

    loadReviews();
  }, []);

  return (
    <div className="app-layout">
      <Sidebar />

      <main className="main-content">
        <Topbar />

        <section className="recent-reviews-section">
          <div className="section-heading">
            <div>
              <span className="section-kicker">Review history</span>
              <h1>All Reviews</h1>
            </div>
          </div>

          {isLoading ? (
            <div className="empty-reviews">Loading reviews...</div>
          ) : reviews.length === 0 ? (
            <div className="empty-reviews">
              <GitPullRequest size={19} />
              <span>Your completed reviews will appear here.</span>
            </div>
          ) : (
            <div className="recent-review-list">
              {reviews.map((review) => (
                <Link
                  className="recent-review-row"
                  to={`/review/${review.id}`}
                  key={review.id}
                >
                  <span className="review-row-icon">
                    <GitPullRequest size={16} />
                  </span>
                  <span className="review-row-main">
                    <strong>{review.repositoryName || "Repository review"}</strong>
                    <small>Pull request #{review.pullRequestId || "-"}</small>
                  </span>
                  <span className="review-row-status">
                    <span className="status-dot" /> Complete
                  </span>
                  <ArrowRight size={16} />
                </Link>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

export default ReviewHistory;