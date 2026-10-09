import {
  codeQuality,
  issueSeverity,
} from "../data/mockData";

function QualityOverview() {
  return (
    <div className="quality-section">

      <div className="quality-card">
        <div className="quality-header">
          <div>
            <h2>Code Quality</h2>
            <p>Overall quality metrics from recent reviews.</p>
          </div>
        </div>

        <div className="quality-list">
          {codeQuality.map((item) => (
            <div className="quality-item" key={item.name}>

              <div className="quality-info">
                <span>{item.name}</span>
                <strong>{item.score}%</strong>
              </div>

              <div className="progress-bar">
                <div
                  className="progress-fill"
                  style={{ width: `${item.score}%` }}
                ></div>
              </div>

            </div>
          ))}
        </div>
      </div>


      <div className="quality-card">
        <div className="quality-header">
          <div>
            <h2>Issue Severity</h2>
            <p>Problems detected across analyzed pull requests.</p>
          </div>
        </div>

        <div className="severity-list">
          {issueSeverity.map((issue) => (
            <div className="severity-item" key={issue.name}>

              <div className="severity-name">
                <span
                  className={`severity-dot ${issue.name.toLowerCase()}`}
                ></span>

                <span>{issue.name}</span>
              </div>

              <strong>{issue.count}</strong>

            </div>
          ))}
        </div>
      </div>

    </div>
  );
}

export default QualityOverview;