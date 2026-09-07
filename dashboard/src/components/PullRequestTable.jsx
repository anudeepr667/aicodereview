import { Link } from "react-router-dom";
import { pullRequests } from "../data/mockData";

function PullRequestTable() {
  return (
    <div className="table-card">
      <div className="table-header">
        <div>
          <h2>Recent Pull Requests</h2>
          <p>Latest pull requests analyzed by CodeReview AI.</p>
        </div>

        <button className="view-all-btn">View All</button>
      </div>

      <div className="table-wrapper">
        <table className="pr-table">
          <thead>
            <tr>
              <th>Pull Request</th>
              <th>Repository</th>
              <th>Author</th>
              <th>Score</th>
              <th>Issues</th>
              <th>Status</th>
            </tr>
          </thead>

          <tbody>
            {pullRequests.map((pr) => (
              <tr key={pr.id}>
                <td>
                  <div className="pr-title">
                    <span>#{pr.id}</span>

                    <Link
                      to={`/reviews/${pr.id}`}
                      className="pr-link"
                    >
                      {pr.title}
                    </Link>
                  </div>
                </td>

                <td>{pr.repository}</td>

                <td>{pr.author}</td>

                <td>
                  <span className="score">
                    {pr.score}
                  </span>
                </td>

                <td>{pr.issues}</td>

                <td>
                  <span className="status-badge">
                    {pr.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default PullRequestTable;