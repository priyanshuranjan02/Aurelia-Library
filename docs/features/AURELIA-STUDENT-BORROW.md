# Student self-borrow and role-aware collection actions

This patch adds a secure student borrowing workflow and fixes staff-only controls appearing in the student UI.

## Changes

- Available books show a **Borrow** button for a signed-in student; unavailable books do not show an active borrow action.
- Students see only their own loans under **My loans**. They do not see staff return controls, the all-records endpoint, or staff-only book/student/librarian creation actions.
- `POST /api/borrow-records/mine` takes only `bookId` and `borrowDate`. The backend resolves the student profile from the authenticated session, so a client cannot choose another `studentId` or supply different borrower contact details.
- `GET /api/borrow-records/mine` only returns loans associated with the signed-in student.
- Only the STUDENT role can access these two `/mine` endpoints. Staff continue using the existing `/api/borrow-records` API.
- The existing pessimistic book lock remains the shared service path for staff and student borrowing.
- CSRF is not disabled; browser POSTs must include the established `XSRF-TOKEN`/`X-XSRF-TOKEN` flow.

## Apply

Extract the ZIP so it contains the `aurelia-student-borrow-patch/` folder. From the repository root, run:

```bash
rsync -av --exclude='.DS_Store' "$HOME/Downloads/aurelia-student-borrow-patch/" ./
./mvnw clean test
node --input-type=module --check < backend/src/main/resources/static/js/main.js
git diff --check
```

## Manual checks

1. Sign in as a STUDENT. The Book collection should show a Borrow button for each available book and no Add/Edit/Delete controls.
2. Click Borrow. The book should become On loan and appear under **My loans**.
3. Sign in as an ADMIN or LIBRARIAN: the existing staff Record a borrow and Return flows should still work.
4. A student must not be able to call `GET /api/borrow-records` or `POST /api/borrow-records` directly.
5. The new test checks that a malicious extra `studentId` in the student request body does not override the student resolved from the session.
