package ru.mirea.library.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Заявка читателя на выдачу книги.
 */
public class BookRequest {

    private int id;
    private int readerId;
    private String bookTitle;
    private String bookAuthor;
    private String isbn;
    private RequestStatus status;
    private LocalDateTime createdAt;
    private LocalDate desiredReturnDate;
    private String comment;

    public BookRequest() {
    }

    /** Конструктор для создания новой заявки (id и createdAt сгенерирует БД). */
    public BookRequest(int readerId,
                       String bookTitle,
                       String bookAuthor,
                       String isbn,
                       LocalDate desiredReturnDate,
                       String comment) {
        this.readerId = readerId;
        this.bookTitle = bookTitle;
        this.bookAuthor = bookAuthor;
        this.isbn = isbn;
        this.status = RequestStatus.CREATED;
        this.desiredReturnDate = desiredReturnDate;
        this.comment = comment;
    }

    /** Полный конструктор — для чтения из БД. */
    public BookRequest(int id,
                       int readerId,
                       String bookTitle,
                       String bookAuthor,
                       String isbn,
                       RequestStatus status,
                       LocalDateTime createdAt,
                       LocalDate desiredReturnDate,
                       String comment) {
        this.id = id;
        this.readerId = readerId;
        this.bookTitle = bookTitle;
        this.bookAuthor = bookAuthor;
        this.isbn = isbn;
        this.status = status;
        this.createdAt = createdAt;
        this.desiredReturnDate = desiredReturnDate;
        this.comment = comment;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getReaderId() { return readerId; }
    public void setReaderId(int readerId) { this.readerId = readerId; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public String getBookAuthor() { return bookAuthor; }
    public void setBookAuthor(String bookAuthor) { this.bookAuthor = bookAuthor; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDate getDesiredReturnDate() { return desiredReturnDate; }
    public void setDesiredReturnDate(LocalDate desiredReturnDate) { this.desiredReturnDate = desiredReturnDate; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    @Override
    public String toString() {
        return String.format(
            "BookRequest{id=%d, readerId=%d, книга='%s' (%s), статус=%s, создана=%s, вернуть до=%s}",
            id, readerId, bookTitle, bookAuthor, status, createdAt, desiredReturnDate
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookRequest that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}