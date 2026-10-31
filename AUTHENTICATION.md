# Authentication Setup

The application uses Spring Security session authentication with BCrypt passwords, HTTP-only session cookies, and a readable CSRF cookie for the Vite frontend.

## Environment variables

Keep these values outside source control:

```text
DB_PASSWORD=your-postgres-password
GEMINI_API_KEY=your-gemini-key
GITHUB_TOKEN=your-github-token
GITHUB_WEBHOOK_SECRET=shared-github-webhook-secret
APP_ADMIN_NAME=Initial Administrator
APP_ADMIN_EMAIL=admin@example.com
APP_ADMIN_PASSWORD=use-a-strong-password
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/aicodereview
SPRING_DATASOURCE_USERNAME=postgres
SESSION_COOKIE_SAME_SITE=lax
SESSION_COOKIE_SECURE=false
```

`APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` are optional. When both are set, the backend creates or promotes that email to `ADMIN` at startup. Public registration always creates `USER` accounts and does not accept a role.

## Render backend deployment

The repository does not contain a `render.yaml`; configure the Render Web Service in the dashboard to use the repository root and its `Dockerfile`. The project targets Java 25. The Docker build runs `mvn -B -DskipTests package` in a Maven 3.10.0 / Eclipse Temurin 25 build image (the local Maven wrapper is pinned to 3.9.16). The runtime uses Eclipse Temurin 25 and starts the packaged JAR with `java -Dserver.port=${PORT:-10000} -jar /app/app.jar`. Render supplies `PORT`; the container defaults to `10000`.

Configure these Render environment variables without committing their values:

- `SPRING_DATASOURCE_URL`: JDBC URL for the Render PostgreSQL database, preferably its internal hostname, in the form `jdbc:postgresql://<host>:5432/<database>`.
- `SPRING_DATASOURCE_USERNAME`: PostgreSQL user. `SPRING_DATASOURCE_PASSWORD` may be used as Spring Boot's standard override; otherwise supply the existing `DB_PASSWORD` value.
- `GEMINI_API_KEY` and `GITHUB_TOKEN`: the credentials required by the AI and GitHub integrations.
- `GITHUB_WEBHOOK_SECRET`: set this same secret in Render and the GitHub webhook configuration if webhook-triggered reviews are enabled; webhook processing is disabled when it is unset.
- `APP_CORS_ALLOWED_ORIGINS`: exactly `https://aicodereview-t83i.vercel.app` (no trailing slash, spaces, or wildcard).
- `SESSION_COOKIE_SAME_SITE`: `None`.
- `SESSION_COOKIE_SECURE`: `true`.
- `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD`: optional; set both only if startup admin provisioning is intended.

`SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME` default to the local PostgreSQL settings above. `spring.jpa.hibernate.ddl-auto=update` remains enabled, so back up the production database before schema-affecting releases. No production secret should be placed in the repository or emitted in logs.

The session cookie must be `SameSite=None; Secure` because Vercel and Render are different sites; the CSRF cookie is already configured with those attributes. Local development defaults to `SameSite=Lax` and a non-secure session cookie, which works over local HTTP.

## Vercel frontend deployment

Set the Vercel project root to `frontend`, build with `npm run build`, and publish `dist`. The checked-in `frontend/vercel.json` rewrites SPA routes to `index.html`. Set `VITE_API_BASE_URL` to `https://aicodereview-ldfn.onrender.com`; Vite embeds this value at build time, so redeploy after changing it. For local development, omit it to use `http://localhost:8080`.

The frontend sends credentials and obtains a CSRF token before registration and login. The token response is XOR-masked, so the backend must accept both that header form and the raw token used by cookie-reading clients. Do not remove CSRF protection or broaden credentialed CORS to wildcard origins.

## Deployment verification notes

The local Git `main` HEAD should be compared with the Render service's deployed commit in its dashboard; the repository contains no Render deployment manifest or public deployment metadata that proves which commit is running or whether auto-deploy from `main` is enabled. Confirm the Vercel Git connection and production deployment settings in the Vercel dashboard as well.

## Running locally

1. Start PostgreSQL and ensure the `aicodereview` database exists.
2. Export the variables above in the backend process environment.
3. Start the backend with `mvnw.cmd spring-boot:run`.
4. Start the frontend from `frontend` with `npm run dev`.
5. Open the Vite URL and register a user or sign in with the bootstrapped admin account.

The current project uses `spring.jpa.hibernate.ddl-auto=update`, so Hibernate adds the `app_users` table and nullable `reviews.user_id` column without reassigning existing reviews. Existing rows without an owner are retained and are visible to administrators; ordinary users see reviews created by their own account.

## Authentication endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/csrf`
- `GET /api/admin/status` for `ADMIN` users only
