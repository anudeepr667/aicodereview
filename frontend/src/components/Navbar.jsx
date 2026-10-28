import { createElement } from "react";
import { BarChart3, Bot, History as HistoryIcon, LayoutDashboard, LogOut, ScanLine, Webhook } from "lucide-react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../context/useAuth";

const links = [
  { to: "/dashboard", label: "Home", icon: LayoutDashboard },
  { to: "/", label: "Review PR", icon: ScanLine, end: true },
  { to: "/history", label: "History", icon: HistoryIcon },
  { to: "/analytics", label: "Analytics", icon: BarChart3 },
  { to: "/webhook-activity", label: "Webhook activity", icon: Webhook, adminOnly: true },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  if (!user) return null;

  return (
    <header className="navbar-wrap">
      <nav className="navbar" aria-label="Primary navigation">
        <NavLink to="/" className="brand" aria-label="AI Code Review home">
          <span className="brand-mark"><Bot size={19} /></span>
          <span>AI Code Review</span>
        </NavLink>
        <div className="nav-links">
          {links.filter(({ adminOnly }) => !adminOnly || user.role === "ADMIN").map(({ to, label, icon: Icon, end }) => (
            <NavLink key={label} to={to} end={end} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
              {createElement(Icon, { size: 16 })}
              <span>{label}</span>
            </NavLink>
          ))}
        </div>
        <div className="nav-account">
          <button className="nav-logout" type="button" onClick={logout} title="Sign out">
            <LogOut size={15} />
            <span>Sign out</span>
          </button>
        </div>
      </nav>
    </header>
  );
}