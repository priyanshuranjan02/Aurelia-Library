# Aurelia profile management

This patch adds self-service profile editing, username changes, password changes, and permanent account deletion for all roles.

## Behavior and safeguards

- Every role can update its own username. Students can also update name, email, and phone. Librarians can update name, optional email, phone, and age. Administrator display name follows the administrator username because the current account model has no separate administrator profile table.
- Duplicate usernames are rejected. A username change updates the authenticated session principal in the same request so the next request continues to work.
- Password changes require the current password, enforce the existing 8-character minimum, and verify the confirmation. Passwords remain hashed using the application's configured `PasswordEncoder`.
- Account deletion requires the current password and exact username confirmation. A student with an outstanding loan cannot delete the account.
- When a student deletes their account, old borrow records remain for circulation history, but the student link and name/email/phone snapshot are anonymized. The account and student profile are deleted.
- Librarian profiles, accounts, and associated invitation records containing their personal details are removed together. An administrator cannot delete the last active administrator. The administrator's invitation records are preserved but no longer reference the deleted account.
- The `V3__allow_deleted_admin_inviter_reference.sql` migration relaxes `librarian_invitations.invited_by_account_id` so invitation history can survive administrator deletion.
- Role assignments cannot be changed via the self-service profile endpoint.

## Files changed

- `backend/src/main/java/com/example/lms/controller/ProfileController.java`
- `backend/src/main/java/com/example/lms/service/ProfileService.java`
- `backend/src/main/java/com/example/lms/dto/ProfileResponse.java`
- `backend/src/main/java/com/example/lms/dto/ProfileUpdateRequest.java`
- `backend/src/main/java/com/example/lms/dto/PasswordChangeRequest.java`
- `backend/src/main/java/com/example/lms/dto/AccountDeletionRequest.java`
- Repositories for accounts, student loans, librarians and invitations
- `backend/src/main/resources/db/migration/V3__allow_deleted_admin_inviter_reference.sql`
- Aurelia profile page, API wrapper, and responsive styles
- `backend/src/test/java/com/example/lms/ProfileManagementIntegrationTest.java`

## Apply and verify

1. Make a fresh database backup before applying this patch.
2. Copy the contents of this ZIP into the existing repository root, allowing the matching files to be replaced. Do not overwrite `.env`, local application configuration, or other credentials.
3. Run `./mvnw clean test` from `backend`.
4. Start the app with the existing environment variables and test profile edits, username changes in the same session, password changes, student deletion after returning all books, and last-administrator protection.
5. Verify `V3` migrated the MySQL database before using account deletion in that installation.

The migration assumes `V2__librarian_invitations.sql` is already applied, as required by the librarian invitation feature.
