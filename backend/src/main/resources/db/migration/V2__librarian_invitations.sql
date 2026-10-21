-- Add an email address for invited librarians. It remains nullable for legacy profiles
-- whose original schema did not collect an email address.
ALTER TABLE librarian_profiles
    ADD COLUMN email VARCHAR(100) NULL;

CREATE TABLE librarian_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    invited_by_account_id BIGINT NOT NULL,
    CONSTRAINT pk_librarian_invitations PRIMARY KEY (id),
    CONSTRAINT uk_librarian_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_librarian_invitations_inviter FOREIGN KEY (invited_by_account_id) REFERENCES accounts (id)
) ENGINE=InnoDB;

CREATE INDEX idx_librarian_invitations_username ON librarian_invitations (username);
CREATE INDEX idx_librarian_invitations_expiry ON librarian_invitations (expires_at);
