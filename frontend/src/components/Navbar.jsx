import { createElement } from "react";
import { Activity, BarChart3, Bot, History as HistoryIcon, LayoutDashboard, ScanLine } from "lucide-react";
import { NavLink } from "react-router-dom";

const links = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/", label: "Review PR", icon: ScanLine, end: true },
  { to: "/history", label: "History", icon: HistoryIcon },
  { to: "/analytics", label: "Analytics", icon: BarChart3 },
];

export default function Navbar() {
  return (
    <header className="navbar-wrap">
      <nav className="navbar" aria-label="Primary navigation">
        <NavLink to="/" className="brand" aria-label="AI Code Review home">
          <span className="brand-mark"><Bot size={19} /></span>
          <span>AI Code Review</span>
        </NavLink>
        <div className="nav-links">
          {links.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={label} to={to} end={end} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
              {createElement(Icon, { size: 16 })}
              <span>{label}</span>
            </NavLink>
          ))}
        </div>
        <span className="nav-status"><Activity size={14} /> Live</span>
      </nav>
    </header>
  );
}