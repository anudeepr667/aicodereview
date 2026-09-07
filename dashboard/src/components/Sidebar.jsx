import {
  LayoutDashboard,
  GitPullRequest,
  SearchCode,
  FolderGit2,
  BarChart3,
  Settings,
  Code2,
} from "lucide-react";

function Sidebar() {
  return (
    <aside className="sidebar">

      <div className="sidebar-logo">
        <Code2 size={28} />
        <h2>CodeReview AI</h2>
      </div>

      <nav className="sidebar-nav">

        <button className="nav-item active">
          <LayoutDashboard size={20} />
          <span>Dashboard</span>
        </button>

        <button className="nav-item">
          <GitPullRequest size={20} />
          <span>Pull Requests</span>
        </button>

        <button className="nav-item">
          <SearchCode size={20} />
          <span>Reviews</span>
        </button>

        <button className="nav-item">
          <FolderGit2 size={20} />
          <span>Repositories</span>
        </button>

        <button className="nav-item">
          <BarChart3 size={20} />
          <span>Analytics</span>
        </button>

      </nav>

      <div className="sidebar-bottom">
        <button className="nav-item">
          <Settings size={20} />
          <span>Settings</span>
        </button>
      </div>

    </aside>
  );
}

export default Sidebar;