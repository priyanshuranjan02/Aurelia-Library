-- Baseline schema for a fresh MySQL 8+ installation.
-- Existing installations are baselined at V1; see docs/SETUP.md before upgrading.

CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BIT(1) NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uk_accounts_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE books (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(200),
    isbn VARCHAR(50),
    published_date DATE,
    available BIT(1) NOT NULL,
    CONSTRAINT pk_books PRIMARY KEY (id),
    CONSTRAINT uk_books_isbn UNIQUE (isbn)
) ENGINE=InnoDB;

CREATE TABLE student_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    account_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    CONSTRAINT pk_student_profiles PRIMARY KEY (id),
    CONSTRAINT uk_student_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_student_profiles_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB;

CREATE TABLE librarian_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    account_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    CONSTRAINT pk_librarian_profiles PRIMARY KEY (id),
    CONSTRAINT uk_librarian_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_librarian_profiles_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB;

CREATE TABLE borrow_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    book_id BIGINT NOT NULL,
    student_id BIGINT,
    borrower_name VARCHAR(100) NOT NULL,
    borrower_email VARCHAR(100) NOT NULL,
    borrower_phone VARCHAR(20) NOT NULL,
    borrow_date DATE NOT NULL,
    return_date DATE,
    CONSTRAINT pk_borrow_records PRIMARY KEY (id),
    CONSTRAINT fk_borrow_records_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_borrow_records_student FOREIGN KEY (student_id) REFERENCES student_profiles (id)
) ENGINE=InnoDB;

CREATE INDEX idx_borrow_records_return_date ON borrow_records (return_date);
