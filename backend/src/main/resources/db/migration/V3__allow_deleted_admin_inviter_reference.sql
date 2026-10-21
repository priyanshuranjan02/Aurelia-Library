-- Invitation history should survive administrator account deletion without retaining
-- a foreign-key reference to the removed account.
ALTER TABLE librarian_invitations
    DROP FOREIGN KEY fk_librarian_invitations_inviter;

ALTER TABLE librarian_invitations
    MODIFY invited_by_account_id BIGINT NULL;

ALTER TABLE librarian_invitations
    ADD CONSTRAINT fk_librarian_invitations_inviter
    FOREIGN KEY (invited_by_account_id) REFERENCES accounts (id) ON DELETE SET NULL;
