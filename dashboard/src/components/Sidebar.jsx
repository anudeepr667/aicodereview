import {
  LayoutDashboard,
  GitPullRequest,
  ScanSearch,
  Code2,
} from "lucide-react";
import { NavLink } from "react-router-dom";

function Sidebar() {
  return (
    <aside className="sidebar">

      <div className="sidebar-logo">
        <Code2 size={28} />
        <h2>CodeReview AI</h2>
      </div>

      <nav className="sidebar-nav">

        <NavLink to="/" end className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}>
          <LayoutDashboard size={20} />
          <span>Dashboard</span>
        </NavLink>

        <NavLink to="/new-review" className="nav-item">
          <GitPullRequest size={20} />
          <span>New Review</span>
        </NavLink>

        <NavLink to="/quick-scan" className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}>
          <ScanSearch size={20} />
          <span>Quick Scan</span>
        </NavLink>

      </nav>

      <div className="sidebar-bottom">
        <span className="sidebar-note">AI-assisted review workspace</span>
      </div>

    </aside>
  );
}

export default Sidebar;