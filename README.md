# Библиотечная информационная система (КР 1)

## Требования
- Java 17+
- Maven 3.9+
- PostgreSQL 17
- JDBC-драйвер `org.postgresql:postgresql` в `pom.xml`

## Запуск
1. Создать БД: `createdb -U postgres library_db`
2. Залить схему: `psql -U postgres -d library_db -f schema.sql`
3. Скопировать `db.properties.example` → `db.properties`, вписать свой пароль
4. Собрать: `mvn clean compile`
5. Запустить: `mvn exec:java "-Dexec.mainClass=ru.mirea.library.Main"`

## Структура
...

ERL-диаграмма:
![alt text](mermaid.png)


## Уже реализовано

### 1 — сущность `Reader`

- Модель `Reader` с полями `id, fullName, email, phone, libraryCardNumber, registeredAt`
- `ReaderRepository` с CRUD и проверками уникальности (`existsByEmail`, `existsByCardNumber`, `hasActiveRequests`)
- `ReaderService` с валидацией email, телефона, номера билета
- `ReaderMenu` — консольное меню

### 2 — сущность `BookRequest`

- Enum `RequestStatus` — 6 статусов + метод `canTransitionTo(next)` + `getRussianName()`

  **Разрешённые переходы статусов:**

  | Откуда | Куда можно |
  |---|---|
  | `CREATED` | `APPROVED`, `CANCELLED`, `REJECTED` |
  | `APPROVED` | `ISSUED`, `CANCELLED` |
  | `ISSUED` | `RETURNED` |
  | `RETURNED`, `CANCELLED`, `REJECTED` | никуда (терминальные) |

- Модель `BookRequest` с полями `id, readerId, bookTitle, bookAuthor, isbn, status, createdAt, desiredReturnDate, comment`
- `BookRequestRepository`:
  - CRUD: `save`, `findById`, `findAll`, `update`, `deleteById`
  - поиск: `findByStatus`, `findByReaderId`, `findByDateRange`
  - поиск по тексту (регистронезависимый, `ILIKE`): `searchByTitle`, `searchByAuthor`, `searchByReaderName`
  - `findByReaderCardNumber`
- `BookRequestService` — бизнес-правила:
  - название и автор обязательны
  - читатель должен существовать
  - дата возврата не в прошлом
  - нельзя удалить заявку со статусом `ISSUED`
  - запрещены недопустимые переходы статусов
- `BookRequestMenu` — консольное меню (создать / список / найти / изменить / удалить / сменить статус)

## Проверка

```powershell
mvn clean compile
mvn exec:java "-Dexec.mainClass=ru.mirea.library.Main"
```

## Известные особенности

- Таблица `readers` использует колонку `library_card_number` (не `card_number`)
- Таблица `book_requests` связана с `readers` через `reader_id`
- Enum `RequestStatus` в БД хранится как `VARCHAR` со значениями в верхнем регистре (`CREATED`, `APPROVED`, ...)