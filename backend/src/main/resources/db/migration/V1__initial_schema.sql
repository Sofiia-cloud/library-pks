CREATE TABLE readers (
    id                   SERIAL PRIMARY KEY,
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(150) NOT NULL UNIQUE,
    phone                VARCHAR(30),
    library_card_number  VARCHAR(30)  NOT NULL UNIQUE,
    registered_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE book_requests (
    id                   SERIAL PRIMARY KEY,
    reader_id            INTEGER      NOT NULL,
    book_title           VARCHAR(200) NOT NULL,
    book_author          VARCHAR(150) NOT NULL,
    isbn                 VARCHAR(20),
    status               VARCHAR(20)  NOT NULL
        CHECK (status IN ('CREATED','APPROVED','ISSUED','RETURNED','CANCELLED','REJECTED')),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    desired_return_date  DATE,
    comment              TEXT,
    CONSTRAINT fk_request_reader
        FOREIGN KEY (reader_id) REFERENCES readers(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_book_requests_reader ON book_requests(reader_id);
CREATE INDEX idx_book_requests_status ON book_requests(status);
