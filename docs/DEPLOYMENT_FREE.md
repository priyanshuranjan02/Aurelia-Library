# Free portfolio deployment: Render + Aiven MySQL + Brevo Email API

This setup targets a small portfolio/demo app. It is **not a production availability design**. As checked in October 2026, Render Free web services sleep after 15 minutes without inbound traffic and block outbound SMTP ports 25, 465, and 587. Aiven's free MySQL tier currently provides 1 GB storage on a single node and can power down after extended inactivity. Brevo's free plan includes 300 email sends per day and retains Brevo branding in sent messages.

Official limits: [Render Free](https://render.com/docs/free), [Aiven MySQL free tier](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier), [Brevo Free limits](https://help.brevo.com/hc/en-us/articles/208580669-FAQs-What-are-the-limits-of-the-Free-plan).

## Architecture

- Render Free Docker web service builds the Spring Boot app and serves both the Aurelia static frontend and `/api` on the same origin. This avoids separate CORS/session-cookie/CSRF configuration.
- Aiven Free MySQL holds persistent data outside Render's ephemeral filesystem.
- Brevo's HTTPS transactional email API sends librarian invitations. The app keeps SMTP for local development but uses `LMS_EMAIL_PROVIDER=brevo` on Render.
- Flyway manages the schema; Hibernate validates it at startup.

## 1. Use the existing Aiven MySQL database

This deployment uses the already-created Aiven service and its `librarydb` database. The existing database has the migrated library records, Flyway schema history, and administrator account `admin-aurelia-library`.

1. Keep using the existing `aurelia-library-db` service and `librarydb` database. Do not recreate the database or restore a full dump over it.
2. The application database user is `lms_app`. Keep its password in `LMS_DB_PASSWORD`, never in the JDBC URL or repository.
3. Use the JDBC URL format shown below, with the exact host and port from Aiven:

   ```text
   jdbc:mysql://<AIVEN_HOST>:<AIVEN_PORT>/librarydb?sslMode=REQUIRED&serverTimezone=UTC
   ```

4. Keep the Aiven pre-migration backup and local database backup outside the repository. Do not delete the local Docker database or its persistent volume.

## 2. Configure Brevo transactional email

1. Create a Brevo account and generate an API key from its dashboard.
2. Register and verify the sender address used by the app. Configure domain authentication as Brevo instructs for your sender domain.
3. Important: a verified sender address alone may not provide reliable delivery to Gmail, Yahoo, and Microsoft recipients. Their sender-authentication requirements generally mean you should use a domain you control and publish the required SPF/DKIM records. Do not send as `@gmail.com` through a third-party sender unless the provider explicitly supports it; you cannot authenticate Google's domain yourself.
4. The application calls `POST https://api.brevo.com/v3/smtp/email` over HTTPS using `LMS_EMAIL_API_KEY`. It does not use SMTP from Render. The app sends a plain-text invitation with the existing one-time link; the token is not returned by the admin API and only its hash is stored.
5. Brevo Free currently has a 300-email/day limit and adds Brevo branding. Check current terms before relying on it.

If you do not own a domain and Brevo will not approve/deliver to your sender, pause invitation-email testing. The app can still deploy and other features work, but reliable invitations need an approved provider/sender. A Gmail API OAuth sender is another option, but it needs a separate OAuth setup and token lifecycle; it is not enabled by this patch.

## 3. Deploy to Render

1. From the repository root, build an image locally if Docker Desktop is available:

   ```bash
   docker build -t aurelia-library:local .
   ```

   This checks the Docker build only; it does not configure a production database.
2. Push the deployment changes on a feature branch and open a PR into `main` after local tests pass.
3. In Render, select **New → Blueprint** and connect the repository containing `render.yaml`. The Blueprint builds the root `Dockerfile` as a Free web service.
4. Enter these values when prompted or in the service's Environment tab. Never commit their values to Git:

   | Variable | Value |
   | --- | --- |
   | `LMS_DB_URL` | Aiven JDBC URL with TLS enabled |
   | `LMS_DB_USERNAME` | Dedicated app database user |
   | `LMS_DB_PASSWORD` | Dedicated app database password |
   | `LMS_EMAIL_API_KEY` | Brevo API key; store as a secret |
   | `LMS_MAIL_FROM` | Verified sender email address (email only, no display-name syntax) |
   | `LMS_PUBLIC_BASE_URL` | Set automatically from Render's `RENDER_EXTERNAL_URL` by the Blueprint |

   `render.yaml` sets `LMS_EMAIL_PROVIDER=brevo`, `LMS_MAIL_FROM_NAME=Aurelia Library`, `SERVER_FORWARD_HEADERS_STRATEGY=framework`, and `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`. Render supplies `PORT`; the Docker entrypoint passes it to Spring Boot.
5. Confirm `LMS_PUBLIC_BASE_URL` matches the service's assigned HTTPS URL. The Blueprint wires it from `RENDER_EXTERNAL_URL`; check the value before testing invitation emails.
6. Watch deployment logs. Confirm Flyway recognizes the existing schema history and Hibernate finishes schema validation. Do not reset or recreate the already-populated Aiven database.
7. Sign in with the existing `admin-aurelia-library` account using the password set during local bootstrap. This Blueprint omits bootstrap variables because the account already exists in Aiven.

## 4. Live smoke tests

- Open the public landing page over HTTPS.
- Register a new student; confirm the server assigns the `STUDENT` role.
- Sign in/out as the existing administrator, a student, and an administrator-created librarian.
- Verify role restrictions, book CRUD, borrowing and returns, and each user's own profile updates.
- Change a username and password; verify the new credentials.
- Try the account-deletion safeguards without deleting your only administrator.
- Send an invitation to an email address you control; check the sender identity and spam folder; redeem the one-time setup link; ensure it cannot be reused.
- Check that no credentials or tokens appear in repository files, browser responses, or logs.

## 5. Operational limitations

- Render Free sleeps after 15 minutes without inbound traffic; the first request after sleeping may take about a minute to wake.
- Render's free filesystem is ephemeral. Do not store the database in the service container or SQLite file.
- Aiven Free MySQL is a 1 GB, single-node demo tier and may power off after extended inactivity. Check for inactivity notices and start the database again from Aiven's console when necessary.
- Export and test-restoring database backups regularly. Do not rely only on the free tier's convenience backup.
- Before any future migration of a populated database, take a backup and test against a disposable copy. This deployment retains its existing Aiven database and backups; never restore the local dump over the hosted database wholesale.
- If the exact free Aiven tier is not available for your account, stop and reassess; do not silently switch to an ephemeral database.
