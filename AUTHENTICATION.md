# Authentication Setup

The application uses Spring Security session authentication with BCrypt passwords, HTTP-only session cookies, and a readable CSRF cookie for the Vite frontend.

## Environment variables

Keep these values outside source control:

```text
DB_PASSWORD=your-postgres-password
GEMINI_API_KEY=your-gemini-key
GITHUB_TOKEN=your-github-token
APP_ADMIN_NAME=Initial Administrator
APP_ADMIN_EMAIL=admin@example.com
APP_ADMIN_PASSWORD=use-a-strong-password
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

`APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` are optional. When both are set, the backend creates or promotes that email to `ADMIN` at startup. Public registration always creates `USER` accounts and does not accept a role.

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
