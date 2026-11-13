-- Сохраняем числовые номера, UUID становятся ключами записей.
ALTER TABLE readers ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE book_requests ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE book_requests ADD COLUMN reader_uuid UUID;

UPDATE book_requests br
SET reader_uuid = r.uuid
FROM readers r
WHERE r.id = br.reader_id;

ALTER TABLE book_requests ALTER COLUMN reader_uuid SET NOT NULL;
ALTER TABLE book_requests DROP CONSTRAINT fk_request_reader;
ALTER TABLE readers DROP CONSTRAINT readers_pkey;
ALTER TABLE book_requests DROP CONSTRAINT book_requests_pkey;

ALTER TABLE readers RENAME COLUMN id TO reader_number;
ALTER TABLE readers RENAME COLUMN uuid TO id;
ALTER TABLE book_requests RENAME COLUMN id TO request_number;
ALTER TABLE book_requests RENAME COLUMN uuid TO id;
ALTER TABLE book_requests DROP COLUMN reader_id;
ALTER TABLE book_requests RENAME COLUMN reader_uuid TO reader_id;

ALTER TABLE readers ADD PRIMARY KEY (id);
ALTER TABLE readers ADD CONSTRAINT uq_readers_number UNIQUE (reader_number);
ALTER TABLE book_requests ADD PRIMARY KEY (id);
ALTER TABLE book_requests ADD CONSTRAINT uq_book_requests_number UNIQUE (request_number);
ALTER TABLE book_requests ADD CONSTRAINT fk_request_reader
    FOREIGN KEY (reader_id) REFERENCES readers(id) ON DELETE RESTRICT;
CREATE INDEX idx_book_requests_reader ON book_requests(reader_id);

ALTER SEQUENCE readers_id_seq RENAME TO readers_number_seq;
ALTER SEQUENCE book_requests_id_seq RENAME TO book_requests_number_seq;

-- Учитываем старые записи, которые могли быть загружены с явными ID.
SELECT setval('readers_number_seq',
    GREATEST((SELECT last_value FROM readers_number_seq),
             COALESCE((SELECT MAX(reader_number) FROM readers), 1)),
    (SELECT is_called FROM readers_number_seq) OR EXISTS (SELECT 1 FROM readers));
SELECT setval('book_requests_number_seq',
    GREATEST((SELECT last_value FROM book_requests_number_seq),
             COALESCE((SELECT MAX(request_number) FROM book_requests), 1)),
    (SELECT is_called FROM book_requests_number_seq) OR EXISTS (SELECT 1 FROM book_requests));

ALTER TABLE readers
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE readers SET updated_at = registered_at;
ALTER TABLE readers ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE readers ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE readers ADD CONSTRAINT ck_readers_version CHECK (version >= 0);

ALTER TABLE book_requests
    ADD COLUMN requested_copies INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN urgent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE book_requests SET updated_at = created_at;
ALTER TABLE book_requests ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE book_requests ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE book_requests ADD CONSTRAINT ck_book_requests_copies CHECK (requested_copies > 0);
ALTER TABLE book_requests ADD CONSTRAINT ck_book_requests_version CHECK (version >= 0);
CREATE INDEX idx_book_requests_archived_created ON book_requests(archived, created_at, id);
CREATE INDEX idx_book_requests_title ON book_requests(book_title);
CREATE INDEX idx_book_requests_author ON book_requests(book_author);
