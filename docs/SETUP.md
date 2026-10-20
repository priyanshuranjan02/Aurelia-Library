# Local Setup

## Prerequisites

- Java 21 or later
- MySQL 8+ or MySQL-compatible local instance
- A database user able to create/use `librarydb`

## Configuration

Set configuration through environment variables. Do not put real database credentials in source control.

| Variable | Default | Purpose |
| --- | --- | --- |
| `LMS_DB_URL` | `jdbc:mysql://localhost:3306/librarydb?...` | MySQL JDBC URL |
| `LMS_DB_USERNAME` | `root` | MySQL user |
| `LMS_DB_PASSWORD` | empty | MySQL password |
| `LMS_BOOTSTRAP_ADMIN_USERNAME` | empty | Optional initial administrator username |
| `LMS_BOOTSTRAP_ADMIN_PASSWORD` | empty | Optional initial administrator password; only used if that username does not yet exist |

On the first setup, provide both administrator variables. The password is BCrypt-hashed before it is stored. If a matching account already exists, startup does not reset its password. The application no longer creates a predictable administrator account from hard-coded source defaults.

## Run

From the repository root, set the environment values for your own machine:

```bash
export LMS_DB_USERNAME=your_mysql_user
export LMS_DB_PASSWORD='your_mysql_password'
export LMS_BOOTSTRAP_ADMIN_USERNAME=admin
export LMS_BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-unique-password'
./mvnw spring-boot:run
```

Open <http://localhost:8080>.

## Database migration behaviour

Flyway manages schema creation for fresh databases using `backend/src/main/resources/db/migration/V1__create_library_schema.sql`. Hibernate is configured to validate the mapped schema rather than silently altering it.

For an existing non-empty database with no Flyway history table, Flyway's configured baseline behavior marks the schema at version 1, then Hibernate validates the mapped tables and columns. **Back up the database first.** Test this transition against a copy of the real database before deployment. If schema validation fails, stop and reconcile the schema with a reviewed migration; do not drop or recreate production tables to make startup pass.

For isolated tests, Flyway is disabled and H2 creates a disposable schema from the JPA entity mappings.

## CSRF and Sessions

The frontend requests `/api/auth/csrf`, receives a readable `XSRF-TOKEN` cookie, and forwards it as `X-XSRF-TOKEN` for state-changing Fetch requests. Authentication is session-based; no JWT is used in v1.0.0.
