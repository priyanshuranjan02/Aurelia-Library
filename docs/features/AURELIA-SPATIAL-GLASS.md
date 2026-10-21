# Aurelia Library — Spatial Glass frontend patch

This patch refreshes the existing static HTML/CSS/JavaScript frontend only. It does not change the Spring Boot backend, database schema, REST endpoint contract, session authentication, CSRF handling, or build dependencies.

## Included

- Spatial/glass visual system: indigo/violet, jade highlights, layered translucent panels, depth/orbit details, refined spacing, and responsive breakpoints.
- Split-screen login view with password visibility toggle and inline error feedback.
- Role-aware sidebar navigation and account identity.
- Dashboard metrics, recent circulation, and quick actions.
- Searchable book catalogue, students, librarians, and circulation records.
- Working create/edit/delete controls for resources whose endpoints already exist, plus return-book action.
- Borrow modal with available-book and student selectors; reader contact details are populated from the selected student profile.
- Accessible focus styles, skip navigation, modal close behavior, reduced-motion support, and screen-reader status/toast regions.

## Compatibility

The implementation uses the existing vanilla ES modules and current API clients. It retains `requestJson` as the only fetch boundary, so the existing `XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header flow and cookie-based session remain in place. API endpoint paths are unchanged.

## Apply from the repository root

1. Start on an up-to-date `main` branch and create a frontend feature branch:

   ```bash
   git checkout main
   git pull --ff-only origin main
   git checkout -b feat/aurelia-spatial-glass
   ```

2. Extract this ZIP to a temporary folder outside the repository. The extracted folder contains the same `backend/...` directory structure as the project. Copy the patch files into the repository root, for example on macOS:

   ```bash
   rsync -av --exclude='.DS_Store' /path/to/aurelia-spatial-glass-patch/ ./
   ```

   Replace `/path/to/aurelia-spatial-glass-patch/` with the actual extracted folder path. Review `git status --short` before proceeding.

3. Run the backend tests:

   ```bash
   ./mvnw clean test
   git diff --check
   ```

4. Start the application with the required MySQL environment variables and initial admin credentials, then review at `http://localhost:8080` on desktop and mobile widths. Test sign-in/sign-out, CSRF-protected creates/updates/deletes, role-based navigation, book search, borrowing, and return.

5. Review the diff before committing. This patch has not been run against the user's local repository, so perform the checks above before committing or opening a PR.

## Important

- No backend or API files are changed by this patch.
- A user with the `STUDENT` role is intentionally given the profile-only workspace; administrators and librarians receive the management pages permitted by the existing Spring Security rules.
- Borrowing requires a book that is available and an existing student. The server remains the source of truth for authorization, borrower details, CSRF validation, and concurrent book availability.
- Delete operations can be rejected by the backend when a record is protected by borrowing history or other database constraints; those errors are surfaced in the UI.
