import { useCallback, useEffect, useState } from "react";
import { Activity, LoaderCircle, RefreshCw, Webhook } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { getWebhookDeliveries } from "../services/api";

const STATUS_LABELS = {
  RECEIVED: "Received",
  PROCESSING: "Processing",
  COMPLETED: "Completed",
  FAILED: "Failed",
  IGNORED: "Ignored"
};

function deliveryDate(value) {
  if (!value) return "Timestamp unavailable";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Timestamp unavailable" : date.toLocaleString();
}

export default function WebhookActivity() {
  const { user } = useAuth();
  const [deliveries, setDeliveries] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [showSetupHelp, setShowSetupHelp] = useState(true);

  const loadDeliveries = useCallback(async () => {
    setIsLoading(true);
    setError("");
    try {
      const data = await getWebhookDeliveries();
      setDeliveries(Array.isArray(data) ? data : []);
    } catch {
      setError("Unable to load webhook activity. Check your connection and try again.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user?.role === "ADMIN") loadDeliveries();
  }, [loadDeliveries, user?.role]);

  if (user?.role !== "ADMIN") {
    return (
      <main className="page-shell">
        <div className="page-alert" role="alert">Webhook activity is available to administrators only.</div>
      </main>
    );
  }

  return (
    <main className="page-shell webhook-page">
      <div className="page-heading">
        <div>
          <p className="section-kicker"><Activity size={14} /> Webhook monitoring</p>
          <h1>Webhook activity</h1>
          <p className="page-subtitle">
            Recent GitHub deliveries and their saved processing status. Retries of one delivery reuse its activity record.
          </p>
        </div>
        <div className="page-heading-actions">
          <button
            type="button"
            className="secondary-button webhook-help-toggle"
            onClick={() => setShowSetupHelp((value) => !value)}
            aria-expanded={showSetupHelp}
            aria-controls="webhook-help-panel"
          >
            {showSetupHelp ? "Hide setup help" : "Webhook setup"}
          </button>
          <span className="count-badge">{deliveries.length} {deliveries.length === 1 ? "delivery" : "deliveries"}</span>
        </div>
      </div>

      <div className={`webhook-layout${showSetupHelp ? "" : " webhook-layout-collapsed"}`}>
        <div className="webhook-activity-column">
          {error && (
            <div className="page-alert" role="alert">
              {error}
              <button type="button" onClick={loadDeliveries}><RefreshCw size={15} /> Retry</button>
            </div>
          )}

          {isLoading ? (
            <div className="loading-page" role="status"><LoaderCircle className="spin" size={22} /> Loading webhook activity...</div>
          ) : error ? null : deliveries.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon"><Webhook size={22} /></div>
              <h3>No webhook deliveries yet</h3>
              <p>Accepted GitHub webhook deliveries will appear here with their processing status.</p>
            </div>
          ) : (
            <section className="webhook-delivery-list" aria-label="Recent GitHub webhook deliveries">
              {deliveries.map((delivery) => {
                const status = typeof delivery.status === "string" ? delivery.status.toUpperCase() : "";
                const statusLabel = STATUS_LABELS[status] || "Unknown";
                const eventLabel = [delivery.eventType, delivery.action].filter(Boolean).join(".");
                const hasPullRequest = delivery.pullRequestNumber != null;
                return (
                  <article className="webhook-delivery-card" key={delivery.deliveryId}>
                    <div className="webhook-delivery-main">
                      <div className="webhook-delivery-title">
                        <span className="repo-icon"><Webhook size={16} /></span>
                        <div>
                          <strong>{delivery.repositoryName || "Unknown repository"}</strong>
                          <span>
                            {hasPullRequest ? `Pull request #${delivery.pullRequestNumber}` : "No pull request"}
                            {eventLabel ? ` · ${eventLabel}` : ""}
                          </span>
                        </div>
                      </div>
                      <div className="webhook-delivery-meta">
                        <time dateTime={delivery.deliveredAt || undefined}>{deliveryDate(delivery.deliveredAt)}</time>
                        <span className={`webhook-status webhook-status-${status.toLowerCase()}`}>
                          <span className="webhook-status-dot" />{statusLabel}
                        </span>
                      </div>
                    </div>
                    {status === "FAILED" && delivery.errorMessage && (
                      <p className="webhook-delivery-error" role="status">{delivery.errorMessage}</p>
                    )}
                    {delivery.reviewId != null && (
                      <Link className="text-link webhook-review-link" to={`/history?review=${encodeURIComponent(delivery.reviewId)}`}>
                        View saved review #{delivery.reviewId}
                      </Link>
                    )}
                  </article>
                );
              })}
            </section>
          )}
        </div>

        {showSetupHelp && (
          <aside id="webhook-help-panel" className="webhook-help-column" aria-label="Webhook setup help">
            <section className="webhook-setup" aria-labelledby="webhook-setup-heading">
              <div className="webhook-setup-heading">
                <Webhook size={19} aria-hidden="true" />
                <div>
                  <h2 id="webhook-setup-heading">Set up GitHub webhook delivery</h2>
                  <p>Connect pull request events to this backend to create reviews automatically.</p>
                </div>
              </div>

              <div className="webhook-setup-grid">
                <section aria-labelledby="webhook-prerequisites-heading">
                  <h3 id="webhook-prerequisites-heading">Before you begin</h3>
                  <ul>
                    <li>You need permission to manage webhooks for the repository.</li>
                    <li>The backend must be running with <code>GITHUB_WEBHOOK_SECRET</code> set.</li>
                    <li>
                      Configure <code>GITHUB_TOKEN</code> with access to the repository's pull request
                      metadata and source contents. Use the narrowest permissions that work for your
                      repository visibility.
                    </li>
                  </ul>
                </section>

                <section aria-labelledby="webhook-configuration-heading">
                  <h3 id="webhook-configuration-heading">Configure the repository</h3>
                  <ol>
                    <li>In the GitHub repository, open <strong>Settings</strong>, then <strong>Webhooks</strong>, and choose <strong>Add webhook</strong>.</li>
                    <li>
                      Set the Payload URL to your publicly reachable backend URL followed by
                      <code>/api/webhooks/github</code>. GitHub cannot deliver to a private
                      <code>localhost</code> address. For local development, expose the local backend
                      through a temporary, secured public HTTPS tunnel and use the tunnel URL.
                    </li>
                    <li>Choose <strong>application/json</strong> as the content type and enter the same secret configured as <code>GITHUB_WEBHOOK_SECRET</code>.</li>
                    <li>Keep SSL verification enabled and choose <strong>Let me select individual events</strong>; select only <strong>Pull requests</strong>, then save.</li>
                  </ol>
                </section>

                <section aria-labelledby="webhook-events-heading">
                  <h3 id="webhook-events-heading">Events handled</h3>
                  <p>
                    The backend accepts the <code>pull_request</code> event and starts review processing
                    for <code>opened</code>, <code>reopened</code>, and <code>synchronize</code> actions.
                    Other event types and pull request actions are recorded as ignored; they do not start
                    a review.
                  </p>
                </section>

                <section aria-labelledby="webhook-security-heading">
                  <h3 id="webhook-security-heading">Security notes</h3>
                  <ul>
                    <li>Every delivery is checked against GitHub's <code>X-Hub-Signature-256</code> HMAC using the configured secret; requests without a valid signature are rejected.</li>
                    <li>Use HTTPS for any publicly reachable backend, protect the webhook secret and GitHub token, and never paste either into source code, screenshots, or support messages.</li>
                    <li>The delivery endpoint is exempt from browser CSRF and session authentication because GitHub cannot provide them; the signature check is required instead. The delivery-history API is restricted to signed-in administrators.</li>
                  </ul>
                </section>

                <section aria-labelledby="webhook-verification-heading">
                  <h3 id="webhook-verification-heading">Verify a delivery</h3>
                  <ol>
                    <li>After saving the webhook, use GitHub's recent deliveries view to inspect a delivery or redeliver it.</li>
                    <li>A successful acceptance response is HTTP <code>202</code>; processing happens asynchronously, so this response alone does not mean a review is complete.</li>
                    <li>Refresh this activity list and check the stored status. A completed delivery links to its saved review; a failed delivery shows a safe, general error message.</li>
                  </ol>
                </section>

                <section aria-labelledby="webhook-troubleshooting-heading">
                  <h3 id="webhook-troubleshooting-heading">Troubleshooting</h3>
                  <ul>
                    <li><strong>No delivery appears:</strong> confirm GitHub can reach the Payload URL and that the selected event is Pull requests.</li>
                    <li><strong>Unauthorized response:</strong> check that the configured webhook secret exactly matches <code>GITHUB_WEBHOOK_SECRET</code>; do not share the secret while troubleshooting.</li>
                    <li><strong>Bad request:</strong> confirm GitHub is sending the <code>pull_request</code> event with a supported action.</li>
                    <li><strong>Failed status:</strong> check backend logs and repository access for the configured GitHub token, then redeliver from GitHub.</li>
                    <li><strong>History unavailable:</strong> sign in with an administrator account; this page's delivery API is administrator-only.</li>
                  </ul>
                </section>
              </div>
            </section>
          </aside>
        )}
      </div>
    </main>
  );
}
