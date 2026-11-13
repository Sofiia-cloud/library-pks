package ru.mirea.library.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.domain.Reader;
import ru.mirea.library.model.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends Repository<BookRequest, UUID> {
    @Query("select count(b) from BookRequest b")
    long totalRequests();

    @Query("select count(r) from Reader r")
    long totalReaders();

    @Query("select count(b) from BookRequest b where b.status in :statuses")
    long countByStatuses(@Param("statuses") Collection<RequestStatus> statuses);

    @Query("select count(b) from BookRequest b where b.status = :status and b.desiredReturnDate < :today")
    long overdueRequests(@Param("status") RequestStatus status, @Param("today") LocalDate today);

    @Query("select count(b) from BookRequest b where b.createdAt >= :from")
    long requestsSince(@Param("from") LocalDateTime from);

    @Query(value = """
            select coalesce((select count(*) from book_requests)::double precision
                / nullif((select count(*) from readers), 0), 0)
            """, nativeQuery = true)
    double averageRequestsPerReader();

    @Query("select b.status as status, count(b) as count from BookRequest b group by b.status")
    List<StatusCount> countGroupedByStatus();

    @Query("""
            select b.bookTitle as name, count(b) as count from BookRequest b
            group by b.bookTitle order by count(b) desc, b.bookTitle
            """)
    List<NameCount> topBooks(Pageable pageable);

    @Query("""
            select b.bookAuthor as name, count(b) as count from BookRequest b
            group by b.bookAuthor order by count(b) desc, b.bookAuthor
            """)
    List<NameCount> topAuthors(Pageable pageable);

    @Query("""
            select r.id as id, r.fullName as name, count(b) as count
            from BookRequest b join b.reader r group by r.id, r.fullName
            order by count(b) desc, r.fullName, r.id
            """)
    List<ReaderCount> topReaders(Pageable pageable);

    @Query(value = """
            select r.id as id, r.full_name as name, count(b.id) as count
            from readers r left join book_requests b on b.reader_id = r.id
            group by r.id, r.full_name order by count(b.id) desc, r.full_name, r.id
            """, nativeQuery = true)
    List<ReaderCount> readerActivity();

    @Query("""
            select r from Reader r where not exists
                (select b.id from BookRequest b where b.reader = r)
            order by r.fullName, r.id
            """)
    List<Reader> readersWithoutRequests();

    @Query(value = """
            select extract(month from created_at)::integer as month, count(*) as count
            from book_requests where created_at >= :from and created_at < :to
            group by extract(month from created_at) order by month
            """, nativeQuery = true)
    List<MonthCount> requestsByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select b.id as id, b.requestNumber as requestNumber, r.id as readerId, r.fullName as readerName,
                b.bookTitle as bookTitle, b.bookAuthor as bookAuthor, b.isbn as isbn, b.status as status,
                b.createdAt as createdAt, b.desiredReturnDate as desiredReturnDate, b.comment as comment
            from BookRequest b join b.reader r order by b.requestNumber
            """)
    List<ExportRow> requestsForExport();

    interface StatusCount {
        RequestStatus getStatus();
        long getCount();
    }

    interface NameCount {
        String getName();
        long getCount();
    }

    interface ReaderCount extends NameCount {
        UUID getId();
    }

    interface MonthCount {
        int getMonth();
        long getCount();
    }

    interface ExportRow {
        UUID getId();
        Integer getRequestNumber();
        UUID getReaderId();
        String getReaderName();
        String getBookTitle();
        String getBookAuthor();
        String getIsbn();
        RequestStatus getStatus();
        LocalDateTime getCreatedAt();
        LocalDate getDesiredReturnDate();
        String getComment();
    }
}
