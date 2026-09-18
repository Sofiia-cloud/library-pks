package ru.mirea.library.repository;

import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.util.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * Репозиторий агрегатных отчётов. Все SQL-запросы — только здесь.
 */
public class ReportRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final String COUNT_BY_STATUS = """
            SELECT COUNT(*) FROM book_requests WHERE status = ?
            """;

    private static final String COUNT_GROUPED_BY_STATUS = """
            SELECT status, COUNT(*) AS cnt
            FROM book_requests
            GROUP BY status
            """;

    private static final String TOP_BOOKS = """
            SELECT book_title AS name, COUNT(*) AS cnt
            FROM book_requests
            GROUP BY book_title
            ORDER BY cnt DESC, book_title
            LIMIT ?
            """;

    private static final String TOP_AUTHORS = """
            SELECT book_author AS name, COUNT(*) AS cnt
            FROM book_requests
            GROUP BY book_author
            ORDER BY cnt DESC, book_author
            LIMIT ?
            """;

    private static final String TOP_READERS = """
            SELECT r.full_name AS name, COUNT(br.id) AS cnt
            FROM readers r
            JOIN book_requests br ON br.reader_id = r.id
            GROUP BY r.id, r.full_name
            ORDER BY cnt DESC, r.full_name
            LIMIT ?
            """;

    private static final String TOTAL_REQUESTS =
            "SELECT COUNT(*) FROM book_requests";

    private static final String ACTIVE_REQUESTS = """
            SELECT COUNT(*) FROM book_requests
            WHERE status IN ('CREATED','APPROVED','ISSUED')
            """;

    private static final String OVERDUE_REQUESTS = """
            SELECT COUNT(*) FROM book_requests
            WHERE status = 'ISSUED'
              AND desired_return_date IS NOT NULL
              AND desired_return_date < CURRENT_DATE
            """;

    private static final String RETURNED_REQUESTS = """
            SELECT COUNT(*) FROM book_requests WHERE status = 'RETURNED'
            """;

    private static final String CANCELLED_OR_REJECTED = """
            SELECT COUNT(*) FROM book_requests WHERE status IN ('CANCELLED','REJECTED')
            """;

    private static final String READER_ACTIVITY = """
            SELECT r.full_name AS name, COUNT(br.id) AS cnt
            FROM readers r
            LEFT JOIN book_requests br ON br.reader_id = r.id
            GROUP BY r.id, r.full_name
            ORDER BY cnt DESC, r.full_name
            """;

    private static final String READERS_WITHOUT_REQUESTS = """
            SELECT r.full_name AS name
            FROM readers r
            LEFT JOIN book_requests br ON br.reader_id = r.id
            WHERE br.id IS NULL
            ORDER BY r.full_name
            """;

    private static final String REQUESTS_BY_MONTH = """
            SELECT EXTRACT(MONTH FROM created_at)::int AS m, COUNT(*) AS cnt
            FROM book_requests
            WHERE EXTRACT(YEAR FROM created_at) = ?
            GROUP BY m
            ORDER BY m
            """;

    private static final String AVG_REQUESTS_PER_READER = """
            SELECT COUNT(br.id)::numeric / NULLIF(COUNT(DISTINCT r.id), 0)
            FROM readers r
            LEFT JOIN book_requests br ON br.reader_id = r.id
            """;

    private static final String COUNT_ALL_READERS =
            "SELECT COUNT(*) FROM readers";

    private static final String REQUESTS_LAST_30_DAYS = """
            SELECT COUNT(*) FROM book_requests
            WHERE created_at >= NOW() - INTERVAL '30 days'
            """;

    public long countByStatus(RequestStatus status) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(COUNT_BY_STATUS)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка подсчёта по статусу", e);
        }
    }

    public Map<String, Long> countGroupedByStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RequestStatus s : RequestStatus.values()) {
            result.put(s.name(), 0L);
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(COUNT_GROUPED_BY_STATUS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("status"), rs.getLong("cnt"));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка группировки по статусам", e);
        }
    }

    public List<Map.Entry<String, Long>> topBooks(int limit) {
        return topQuery(TOP_BOOKS, limit);
    }

    public List<Map.Entry<String, Long>> topAuthors(int limit) {
        return topQuery(TOP_AUTHORS, limit);
    }

    public List<Map.Entry<String, Long>> topReaders(int limit) {
        return topQuery(TOP_READERS, limit);
    }

    private List<Map.Entry<String, Long>> topQuery(String sql, int limit) {
        List<Map.Entry<String, Long>> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(Map.entry(rs.getString("name"), rs.getLong("cnt")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка топ-выборки", e);
        }
    }

    public long totalRequests()         { return simpleCount(TOTAL_REQUESTS); }
    public long activeRequests()        { return simpleCount(ACTIVE_REQUESTS); }
    public long overdueRequests()       { return simpleCount(OVERDUE_REQUESTS); }
    public long returnedRequests()      { return simpleCount(RETURNED_REQUESTS); }
    public long cancelledOrRejected()   { return simpleCount(CANCELLED_OR_REJECTED); }
    public long countAllReaders()       { return simpleCount(COUNT_ALL_READERS); }
    public long requestsLast30Days()    { return simpleCount(REQUESTS_LAST_30_DAYS); }

    private long simpleCount(String sql) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка подсчёта: " + e.getMessage(), e);
        }
    }

    public Map<String, Long> readerActivity() {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(READER_ACTIVITY);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("name"), rs.getLong("cnt"));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка активности читателей", e);
        }
    }

    public List<String> readersWithoutRequests() {
        List<String> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(READERS_WITHOUT_REQUESTS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("name"));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска читателей без заявок", e);
        }
    }

    public Map<Integer, Long> requestsByMonth(int year) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            result.put(m, 0L);
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(REQUESTS_BY_MONTH)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("m"), rs.getLong("cnt"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка статистики по месяцам", e);
        }
    }

    public double averageRequestsPerReader() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(AVG_REQUESTS_PER_READER);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка среднего числа заявок", e);
        }
    }
}