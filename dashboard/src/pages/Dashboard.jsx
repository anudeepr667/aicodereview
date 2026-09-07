import Sidebar from "../components/Sidebar";
import StatCard from "../components/StatCard";
import PullRequestTable from "../components/PullRequestTable";
import { stats } from "../data/mockData";
import QualityOverview from "../components/QualityOverview";
import { Link } from "react-router-dom";


function Dashboard() {
  return (
    <div className="app-layout">

      <Sidebar />

      <main className="main-content">

        <div className="dashboard-header">
          <div>
            <h1>Dashboard</h1>
            <p>Monitor your repositories and AI code reviews.</p>
          </div>

         <Link
  to="/new-review"
  className="new-review-btn"
>
  Analyze Pull Request
</Link>
        </div>

        <div className="stats-grid">
          {stats.map((stat) => (
            <StatCard
              key={stat.title}
              title={stat.title}
              value={stat.value}
              change={stat.change}
            />
          ))}
        </div>

        <PullRequestTable />
        <QualityOverview />

      </main>

    </div>
  );
}

export default Dashboard;