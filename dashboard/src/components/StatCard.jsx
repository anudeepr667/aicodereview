function StatCard({ title, value, change }) {
  return (
    <div className="stat-card">
      <p className="stat-title">{title}</p>

      <div className="stat-content">
        <h2>{value}</h2>
        <span className="stat-change">{change}</span>
      </div>
    </div>
  );
}

export default StatCard;