package ru.mirea.library.repository;

import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.util.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookRequestRepository implements Repository<BookRequest, Integer> {

    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final String INSERT_SQL = """
            INSERT INTO book_requests (reader_id, book_title, book_author, isbn,
                                       status, desired_return_date, comment)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id, created_at
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests ORDER BY id
            """;

    private static final String UPDATE_SQL = """
            UPDATE book_requests
            SET reader_id = ?, book_title = ?, book_author = ?, isbn = ?,
                status = ?, desired_return_date = ?, comment = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM book_requests WHERE id = ?";

    private static final String SELECT_BY_STATUS = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests WHERE status = ? ORDER BY id
            """;

    private static final String SELECT_BY_READER_ID = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests WHERE reader_id = ? ORDER BY id
            """;

    private static final String SELECT_BY_DATE_RANGE = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests WHERE created_at BETWEEN ? AND ? ORDER BY created_at
            """;

    private static final String SEARCH_BY_TITLE = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests WHERE book_title ILIKE ? ORDER BY id
            """;

    private static final String SEARCH_BY_AUTHOR = """
            SELECT id, reader_id, book_title, book_author, isbn, status,
                   created_at, desired_return_date, comment
            FROM book_requests WHERE book_author ILIKE ? ORDER BY id
            """;

    private static final String SEARCH_BY_READER_NAME = """
            SELECT br.id, br.reader_id, br.book_title, br.book_author, br.isbn,
                   br.status, br.created_at, br.desired_return_date, br.comment
            FROM book_requests br
            JOIN readers r ON r.id = br.reader_id
            WHERE r.full_name ILIKE ?
            ORDER BY br.id
            """;

    private static final String SELECT_BY_READER_CARD = """
            SELECT br.id, br.reader_id, br.book_title, br.book_author, br.isbn,
                   br.status, br.created_at, br.desired_return_date, br.comment
            FROM book_requests br
            JOIN readers r ON r.id = br.reader_id
            WHERE r.library_card_number = ?
            ORDER BY br.id
            """;

    @Override
    public BookRequest save(BookRequest request) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setInt(1, request.getReaderId());
            ps.setString(2, request.getBookTitle());
            ps.setString(3, request.getBookAuthor());
            ps.setString(4, request.getIsbn());
            ps.setString(5, request.getStatus() == null
                    ? RequestStatus.CREATED.name()
                    : request.getStatus().name());

            if (request.getDesiredReturnDate() != null) {
                ps.setDate(6, Date.valueOf(request.getDesiredReturnDate()));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, request.getComment());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    request.setId(rs.getInt("id"));
                    request.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
            }
            return request;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка сохранения заявки: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<BookRequest> findById(Integer id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заявки по ID", e);
        }
    }

    @Override
    public List<BookRequest> findAll() {
        List<BookRequest> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка получения списка заявок", e);
        }
    }

    @Override
    public boolean update(BookRequest request) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {

            ps.setInt(1, request.getReaderId());
            ps.setString(2, request.getBookTitle());
            ps.setString(3, request.getBookAuthor());
            ps.setString(4, request.getIsbn());
            ps.setString(5, request.getStatus() == null
                    ? RequestStatus.CREATED.name()
                    : request.getStatus().name());

            if (request.getDesiredReturnDate() != null) {
                ps.setDate(6, Date.valueOf(request.getDesiredReturnDate()));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, request.getComment());
            ps.setInt(8, request.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка обновления заявки", e);
        }
    }

    @Override
    public boolean deleteById(Integer id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка удаления заявки", e);
        }
    }

    public List<BookRequest> findByStatus(RequestStatus status) {
        List<BookRequest> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_STATUS)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заявок по статусу", e);
        }
    }

    public List<BookRequest> findByReaderId(int readerId) {
        List<BookRequest> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_READER_ID)) {
            ps.setInt(1, readerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заявок по читателю", e);
        }
    }

    public List<BookRequest> findByDateRange(LocalDateTime from, LocalDateTime to) {
        List<BookRequest> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_DATE_RANGE)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заявок по диапазону дат", e);
        }
    }

    public List<BookRequest> searchByTitle(String query) {
        return searchLike(SEARCH_BY_TITLE, query);
    }

    public List<BookRequest> searchByAuthor(String query) {
        return searchLike(SEARCH_BY_AUTHOR, query);
    }

    public List<BookRequest> searchByReaderName(String query) {
        return searchLike(SEARCH_BY_READER_NAME, query);
    }

    public List<BookRequest> findByReaderCardNumber(String cardNumber) {
        List<BookRequest> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_READER_CARD)) {
            ps.setString(1, cardNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заявок по номеру билета", e);
        }
    }

    private List<BookRequest> searchLike(String sql, String query) {
        List<BookRequest> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заявок", e);
        }
    }

    private BookRequest mapRow(ResultSet rs) throws SQLException {
        BookRequest r = new BookRequest();
        r.setId(rs.getInt("id"));
        r.setReaderId(rs.getInt("reader_id"));
        r.setBookTitle(rs.getString("book_title"));
        r.setBookAuthor(rs.getString("book_author"));
        r.setIsbn(rs.getString("isbn"));
        r.setStatus(RequestStatus.valueOf(rs.getString("status")));
        r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

        Date desired = rs.getDate("desired_return_date");
        if (desired != null) {
            r.setDesiredReturnDate(desired.toLocalDate());
        }

        r.setComment(rs.getString("comment"));
        return r;
    }
}