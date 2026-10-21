# Librarian invitations and password setup

New librarian accounts should be created from **Librarians → Invite a librarian** while signed in as an administrator. The application emails a one-time setup link; the invitee chooses their own password on that page. Public registration remains student-only.

## Security properties

- Creating invitations requires the existing administrator role and a valid CSRF token.
- The application generates a cryptographically random 256-bit URL-safe token. Only its SHA-256 hash is stored in MySQL; the raw token is sent by email and is not returned from the admin API.
- The setup token expires after `LMS_INVITATION_EXPIRY_HOURS` (24 hours by default), is locked and checked during acceptance, and can be consumed only once.
- Completing an invitation always creates the `LIBRARIAN` role on the server. The browser cannot choose a role.
- Librarian passwords must have at least 12 characters and are stored using the existing password encoder.
- The email link carries the token in the URL fragment (`#token=...`) so the browser does not send it in the initial HTTP request or referrer header.
- Flyway V2 adds a nullable email field to legacy librarian profiles and creates the invitation table. Existing librarians are not deleted or disabled.

## SMTP configuration

Email is not available unless SMTP is configured. The invitation endpoint returns HTTP 503 with `INVITATION_EMAIL_NOT_CONFIGURED` if the host or sender address is missing; it will not silently create an invitation the recipient cannot receive.

Configure the following through your environment or hosting dashboard. Never commit real SMTP credentials.

| Variable | Example / purpose |
| --- | --- |
| `LMS_SMTP_HOST` | SMTP host from your email provider |
| `LMS_SMTP_PORT` | `587` for STARTTLS, if required by provider |
| `LMS_SMTP_USERNAME` | SMTP username |
| `LMS_SMTP_PASSWORD` | SMTP/app password stored as a secret |
| `LMS_SMTP_AUTH` | `true` when the provider requires authentication |
| `LMS_SMTP_STARTTLS` | `true` when the provider supports STARTTLS |
| `LMS_MAIL_FROM` | Sender address verified with the provider |
| `LMS_PUBLIC_BASE_URL` | Public application origin, e.g. `https://your-app.example.com` |
| `LMS_INVITATION_EXPIRY_HOURS` | Optional; defaults to `24` |

For local development, set these values only after choosing an SMTP provider. Until configured, the rest of the app and the automated test suite can start normally, but an administrator cannot send a real invitation.

## Endpoints

- `POST /api/librarian-invitations` — administrator-only; creates an invitation and sends the email. It never returns the raw setup token.
- `POST /api/librarian-invitations/accept` — public endpoint for an invitee to set their password; requires the existing CSRF cookie/header flow.
- `GET /librarian-setup.html` — password setup form.

After deploying V2, test an invitation end-to-end with a real mailbox before enabling public production use. Verify delivery, expiry, single-use behavior, wrong-token handling, and that the link uses the configured HTTPS public URL.
