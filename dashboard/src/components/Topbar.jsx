import { Bell, CircleUserRound } from "lucide-react";

function Topbar() {
	return (
		<header className="topbar">
			<div>
				<span className="topbar-kicker">Workspace</span>
				<strong>AI Code Review</strong>
			</div>

			<div className="topbar-actions">
				<button type="button" className="icon-button" aria-label="Notifications">
					<Bell size={18} />
				</button>
				<span className="user-chip">
					<CircleUserRound size={20} />
					Demo user
				</span>
			</div>
		</header>
	);
}

export default Topbar;
