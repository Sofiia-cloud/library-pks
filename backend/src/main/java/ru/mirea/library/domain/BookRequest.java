package ru.mirea.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import ru.mirea.library.model.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "book_requests")
public class BookRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "request_number", nullable = false, unique = true, insertable = false, updatable = false)
    private Integer requestNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false)
    private Reader reader;

    @Column(name = "book_title", nullable = false, length = 200)
    private String bookTitle;

    @Column(name = "book_author", nullable = false, length = 150)
    private String bookAuthor;

    @Column(length = 20)
    private String isbn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "desired_return_date")
    private LocalDate desiredReturnDate;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "requested_copies", nullable = false)
    private int requestedCopies = 1;

    @Column(nullable = false)
    private boolean urgent;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected BookRequest() {
    }

    public BookRequest(Reader reader, String bookTitle, String bookAuthor, String isbn,
                       LocalDate desiredReturnDate, String comment) {
        this.reader = reader;
        this.bookTitle = bookTitle;
        this.bookAuthor = bookAuthor;
        this.isbn = isbn;
        this.desiredReturnDate = desiredReturnDate;
        this.comment = comment;
    }

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public Integer getRequestNumber() { return requestNumber; }
    public Reader getReader() { return reader; }
    public void setReader(Reader reader) { this.reader = reader; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getBookAuthor() { return bookAuthor; }
    public void setBookAuthor(String bookAuthor) { this.bookAuthor = bookAuthor; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDate getDesiredReturnDate() { return desiredReturnDate; }
    public void setDesiredReturnDate(LocalDate desiredReturnDate) { this.desiredReturnDate = desiredReturnDate; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public int getRequestedCopies() { return requestedCopies; }
    public void setRequestedCopies(int requestedCopies) { this.requestedCopies = requestedCopies; }
    public boolean isUrgent() { return urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof BookRequest request && getId() != null && getId().equals(request.getId());
    }

    @Override
    public int hashCode() {
        return BookRequest.class.hashCode();
    }
}
