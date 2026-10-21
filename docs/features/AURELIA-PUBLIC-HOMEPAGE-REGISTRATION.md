# Aurelia public homepage and student self-registration

This patch adds a public landing page and a secure self-service student-registration path to the existing Aurelia Spatial Glass frontend.

## Included

- Public home view is now the default route when a visitor opens `/`.
- Clear navigation between public homepage, sign-in, and student registration.
- Student registration form with client-side checks and server-side Jakarta validation.
- `POST /api/auth/register` creates a student profile and always assigns `Role.STUDENT` server-side.
- Public registration still requires a valid CSRF token; login/session auth and existing staff role restrictions remain enabled.
- Tests cover registration success, duplicate username handling, password hashing/no password in the response, ignoring client role elevation, and CSRF enforcement.

## Apply to the current `feat/aurelia-spatial-glass` branch

Extract this ZIP into a folder outside the repository, then from the repository root run:

```bash
rsync -av --exclude='.DS_Store' /path/to/aurelia-public-registration-patch/ ./
./mvnw clean test
node --input-type=module --check < backend/src/main/resources/static/js/main.js
node --input-type=module --check < backend/src/main/resources/static/js/api/auth-api.js
node --input-type=module --check < backend/src/main/resources/static/js/api/http.js
git diff --check
```

Replace `/path/to/aurelia-public-registration-patch/` with the actual extracted directory.

## Manual verification

1. Open `http://localhost:8080/` in a fresh browser session. The public homepage should appear rather than the sign-in form.
2. Choose **Create student account** and register with a unique username, valid email, phone number, and password of at least 8 characters.
3. Verify that the form redirects to sign-in with the username prefilled. Sign in with the new credentials.
4. The new account should enter the `STUDENT` workspace. Students can browse the book collection and view their profile, but cannot access staff management, dashboard, borrowing-management actions, or librarian pages.
5. Attempt a duplicate username and confirm it gets a readable conflict message.
6. Confirm an unauthenticated request cannot access `/api/students`, `/api/librarians`, borrowing, or the dashboard. Do not disable CSRF.

## Security notes

- This public form creates **student accounts only**. It does not permit selection of a role.
- Staff accounts remain administrator-managed.
- CSRF protection remains enabled for the registration POST; the client bootstraps the existing `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header.
- Passwords go through the existing BCrypt `PasswordEncoder`; the registration response contains no password or password hash.
- Do not enable public administrator/librarian registration.
