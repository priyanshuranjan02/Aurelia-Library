# Testing

## Automated Tests

Run all tests from the repository root:

```bash
./mvnw clean test
```

The suite uses H2 in MySQL compatibility mode and covers:

- Authentication, logout, session reuse, and protected routes.
- Browser-equivalent CSRF cookie/header login and logout flow.
- Books, students, librarians, borrow, and return workflows.
- Concurrent attempts to borrow the same book (one request should succeed; the other should receive a controlled `BOOK_UNAVAILABLE` response).
- Validation errors, duplicate ISBN conflict handling, and JSON 401/403 responses.
- Repository-level ISBN uniqueness and auditing timestamps.

## Manual Verification

Use [testing/black-box-test-cases.csv](testing/black-box-test-cases.csv) against a local MySQL instance. Capture release evidence in `../screenshots/desktop` and `../screenshots/mobile` for login, CRUD, validation, borrow/return, role restrictions, and responsive navigation.

## Migration Verification

Before releasing Flyway changes, verify both paths against MySQL 8+:

1. Start with a blank disposable database and confirm `V1__create_library_schema.sql` creates the tables.
2. Start against a backup copy of the existing schema and confirm Flyway baseline plus Hibernate validation succeeds without data loss.

The migration and concurrency test in the hardening change set still require execution in the project environment before they should be marked verified.
