# GitHub Webhook Setup

This application can accept pull request webhooks at `POST /api/webhooks/github`.
The endpoint verifies GitHub's `X-Hub-Signature-256` header before parsing or
recording a delivery. Configure the secret through the `GITHUB_WEBHOOK_SECRET`
environment variable; the endpoint fails closed when it is absent.

## Configure the application

1. Generate a unique random webhook secret and store it in your deployment's
   secret manager or environment. Do not commit it to source control.
2. Set `GITHUB_WEBHOOK_SECRET` for the application process.
3. Set `GITHUB_TOKEN` to a GitHub token that can read the target repositories
   and pull request contents. Keep the existing `GEMINI_API_KEY` configured
   for the review service.
4. Start the application with its PostgreSQL database available. Hibernate
   creates/updates the `github_webhook_deliveries` table and adds a nullable,
   unique `webhook_delivery_id` column to `reviews` under the current
   `spring.jpa.hibernate.ddl-auto=update` configuration.

Webhook reviews have no authenticated application user. Their `reviews.user_id`
is left null rather than associating them with an arbitrary account. The
existing user review listing remains user-scoped, and webhook-created records
are visible only to administrators through the existing all-reviews path.

## Configure a repository webhook

In the GitHub repository, open **Settings → Webhooks → Add webhook** and set:

- **Payload URL:** `https://<public-host>/api/webhooks/github`
- **Content type:** `application/json`
- **Secret:** the same value configured as `GITHUB_WEBHOOK_SECRET`
- **Events:** select **Let me select individual events**, then select **Pull requests**
- **Active:** enabled

GitHub sends a unique `X-GitHub-Delivery` ID and the `pull_request` event
header. `opened`, `reopened`, and `synchronize` actions are queued for analysis.
Other actions/events are durably recorded as ignored. Valid deliveries receive
HTTP 202; invalid signatures receive HTTP 401 and malformed supported events
receive HTTP 400.

## Local testing with an HTTPS tunnel

GitHub cannot deliver webhooks to a private `localhost` URL. During local
development:

1. Run the application and make sure its PostgreSQL, GitHub, and Gemini
   configuration is available.
2. Start a public HTTPS tunnel to the application's listening port using a
   trusted tool such as `cloudflared` or `ngrok`.
3. Use the tunnel's HTTPS URL followed by `/api/webhooks/github` as the
   repository webhook payload URL.
4. Configure the webhook secret in the local process environment and enter
   that same secret in GitHub.
5. In GitHub's webhook settings, use **Recent deliveries** to inspect the
   response. Remove the tunnel and rotate the secret after testing if it was
   shared beyond the intended development environment.

The focused webhook tests use mocked persistence and collaborators and do not
need a live database or external GitHub/Gemini services. Database-dependent
delivery lifecycle, uniqueness, and full review execution should be verified
in an environment with PostgreSQL and valid external service credentials.

## Delivery behavior and retries

Each delivery ID is stored in `github_webhook_deliveries` with its event,
repository, pull request, head SHA, attempt count, status, and last processing
error. The database unique constraint prevents duplicate delivery records.
Completed and in-progress redeliveries are acknowledged without starting a
second analysis. A failed delivery redelivery requeues processing. Review rows
also store the delivery ID under a unique constraint so a retry cannot create
the same webhook review twice.

Analysis runs on a bounded background executor. `PROCESSING`, `COMPLETED`,
`FAILED`, and `IGNORED` states are stored durably; a failed analysis is
available for GitHub redelivery. The webhook uses the event's head SHA when
retrieving source contents and verifies the live PR head around retrieval of
the pull request diff and changed-file metadata. If the PR has advanced past
the event commit, analysis fails and can be retried with the newer delivery.
