# Applying this proposed hardening change set

This package contains file replacements and additions for `priyanshuranjan02/Library-Management-System`. It has not been committed to GitHub and has not been executed through Maven in this environment.

## What is included

- Flyway dependencies and a versioned baseline schema migration for fresh MySQL installs.
- Hibernate schema validation instead of automatic schema updates.
- Baseline behavior for existing non-empty schemas, followed by Hibernate mapping validation.
- Pessimistic database row locks on book borrowing and loan returns.
- A concurrency integration test that submits two simultaneous borrow requests for the same book and expects one success and one controlled rejection.
- Removal of the committed database password fallback and very verbose security/web logging.
- Initial administrator seeding only when explicit username/password configuration is supplied; isolated test credentials are set in test properties.
- Updated README and local setup instructions.

## Apply safely

1. Create a new branch; do not apply directly to `main`.
2. Back up any existing MySQL database.
3. Copy the included files to matching paths in the repository, preserving the relative directory structure. New files include `backend/src/main/resources/db/migration/V1__create_library_schema.sql` and `backend/src/test/java/com/example/lms/ConcurrentBorrowIntegrationTest.java`.
4. Run `./mvnw clean test` from the repository root.
5. Start the application with a disposable fresh MySQL database and verify that Flyway creates the schema and Hibernate validation succeeds.
6. Test an existing-database upgrade against a copy of the real database before deployment.
7. Only merge after checking behavior against the same MySQL version used in deployment.

## Verification caveat

The migration and concurrency test are prepared from the current entity mappings, but have **not been executed here**. In particular, validate timestamp column types on MySQL and confirm the migration against both a blank database and a copy of the existing schema. If schema validation fails, reconcile the schema with a reviewed migration; do not drop or recreate tables to make startup pass.
