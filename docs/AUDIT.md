# Code Audit Findings — Current Repository Snapshot

Reviewed against the public `main` branch before preparing this change set.

## Confirmed high-priority findings

1. **Concurrent borrowing is not protected by an explicit row lock.** `BorrowRecordService.borrow()` originally used `books.findById()` and then checked `book.isAvailable()`. Two transactions could both observe the book as available before either transaction commits. The proposed patch adds a pessimistic write lock to the book lookup, then checks availability while holding the lock.
2. **Simultaneous return requests are not serialized.** The original `returnBook()` method loaded a record without locking before checking `returnDate`. The proposed patch also locks the borrow record to prevent two requests both treating the same loan as not yet returned.
3. **Schema evolution relies on `spring.jpa.hibernate.ddl-auto=update`.** There was no Flyway dependency or versioned migration. The proposed patch adds a fresh-install migration and schema validation; existing-database baseline behavior must be tested on a backup before release.
4. **Committed configuration includes a database password fallback and verbose logs.** The original `application.properties` set `LMS_DB_PASSWORD` to a fixed fallback and enabled Spring Security `TRACE` and web `DEBUG`. The proposal removes the password fallback and reduces these loggers to `INFO`.
5. **The app auto-seeds a predictable administrator.** The original `LibraryManagementApplication` seeded `admin` using a password hard-coded in source. The proposal makes initial seed credentials explicit environment configuration and adds test-only values in test resources.

## Confirmed frontend gaps

6. **CRUD actions are incomplete in rendered tables.** `main.js` displayed `—` in Books, Students and Librarians action columns; edit and delete actions were not wired in the rendered table.
7. **Borrow form asks for raw IDs.** The modal asks users to type book and student IDs rather than selecting existing books and students by name/title.
8. **Some manual browser verification is still listed as pending** in `docs/TASKS.md`; the current screenshots may not represent all latest UI paths.

## Proposed order after this package

1. Apply this change set on a separate branch and run Maven tests.
2. Validate the Flyway schema against both a fresh MySQL database and a backup copy of the existing database.
3. Implement the spatial/glassmorphism design in the current vanilla HTML/CSS/JavaScript frontend, retaining the Spring Boot-served single-app architecture.
4. Complete all CRUD actions, improve book/student selection in the borrow modal, and verify the responsive UI.
5. Containerize and deploy after configuration and tests are verified.

## Limits of this audit

This is a static source review, not a successful build or live database test. The proposed changes are not committed, and the concurrency test and migration must be executed in the repository environment before claiming they pass.
