
DROP TABLE IF EXISTS book_requests CASCADE;
DROP TABLE IF EXISTS readers CASCADE;

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


INSERT INTO readers (full_name, email, phone, library_card_number, registered_at) VALUES
('Семенов Тихон Павлович',      'semenov@mail.ru',    '+79001112233', 'LIB-0001', '2024-01-15 10:00:00'),
('Иванова Валерия Денисовна',    'ivanova@mail.ru',   '+79002223344', 'LIB-0002', '2024-02-20 11:30:00'),
('Лукьянов Антон Павлович',   'lukyanov@mail.ru',   '+79003334455', 'LIB-0003', '2024-03-10 09:15:00'),
('Головушкина София Владиславовна',  'golovushkina@mail.ru','+79004445566', 'LIB-0004', '2024-04-05 14:45:00'),
('Пальчевский Евгений Владимирович',  'palchevsky@mail.ru',   '+79005556677', 'LIB-0005', '2024-05-12 16:20:00');

INSERT INTO book_requests (reader_id, book_title, book_author, isbn, status, created_at, desired_return_date, comment) VALUES
(1, 'Преступление и наказание', 'Ф. М. Достоевский',  '978-5-04-116640-3', 'CREATED',   '2024-09-01 10:00:00', '2024-10-01', 'Срочно нужна'),
(1, 'Война и мир',              'Л. Н. Толстой',      '978-5-04-118550-3', 'APPROVED',  '2024-09-03 11:00:00', '2024-10-03', NULL),
(2, 'Мастер и Маргарита',       'М. А. Булгаков',     '978-5-04-116110-1', 'ISSUED',    '2024-09-05 12:00:00', '2024-09-25', 'Учебная литература'),
(2, 'Собачье сердце',           'М. А. Булгаков',     NULL,                'RETURNED',  '2024-08-15 09:00:00', '2024-09-01', NULL),
(3, 'Тихий Дон',                'М. А. Шолохов',      '978-5-04-117620-4', 'CREATED',   '2024-09-07 13:30:00', '2024-10-07', NULL),
(3, 'Анна Каренина',            'Л. Н. Толстой',      '978-5-04-118551-0', 'CANCELLED', '2024-09-08 15:00:00', '2024-10-08', 'Передумал'),
(4, 'Отцы и дети',              'И. С. Тургенев',     '978-5-04-116580-5', 'APPROVED',  '2024-09-10 08:45:00', '2024-10-10', NULL),
(4, 'Евгений Онегин',           'А. С. Пушкин',       '978-5-04-116200-9', 'ISSUED',    '2024-09-11 17:00:00', '2024-09-30', NULL),
(5, 'Герой нашего времени',     'М. Ю. Лермонтов',    '978-5-04-116250-4', 'CREATED',   '2024-09-12 09:30:00', '2024-10-12', NULL),
(5, 'Мёртвые души',             'Н. В. Гоголь',       '978-5-04-116300-6', 'RETURNED',  '2024-08-20 10:15:00', '2024-09-05', 'Всё в порядке'),
(2, 'Ревизор',                  'Н. В. Гоголь',       NULL,                'REJECTED',  '2024-09-13 11:45:00', '2024-10-13', 'Нет в наличии'),
(1, 'Идиот',                    'Ф. М. Достоевский',  '978-5-04-116641-0', 'APPROVED',  '2024-09-14 16:00:00', '2024-10-14', NULL);