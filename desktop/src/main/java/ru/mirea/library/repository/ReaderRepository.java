package ru.mirea.library.repository;

import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.Reader;
import ru.mirea.library.util.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReaderRepository implements Repository<Reader, Integer> {

    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final String INSERT_SQL = """
            INSERT INTO readers (full_name, email, phone, library_card_number)
            VALUES (?, ?, ?, ?)
            RETURNING id, registered_at
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, full_name, email, phone, library_card_number, registered_at
            FROM readers WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, full_name, email, phone, library_card_number, registered_at
            FROM readers ORDER BY id
            """;

    private static final String UPDATE_SQL = """
            UPDATE readers
            SET full_name = ?, email = ?, phone = ?, library_card_number = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM readers WHERE id = ?";

    private static final String EXISTS_EMAIL = "SELECT 1 FROM readers WHERE email = ?";
    private static final String EXISTS_CARD  = "SELECT 1 FROM readers WHERE library_card_number = ?";
    private static final String COUNT_ACTIVE_REQUESTS = """
            SELECT COUNT(*) FROM book_requests
            WHERE reader_id = ? AND status IN ('CREATED','APPROVED','ISSUED')
            """;

    @Override
    public Reader save(Reader reader) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setString(1, reader.getFullName());
            ps.setString(2, reader.getEmail());
            ps.setString(3, reader.getPhone());
            ps.setString(4, reader.getLibraryCardNumber());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reader.setId(rs.getInt("id"));
                    reader.setRegisteredAt(rs.getTimestamp("registered_at").toLocalDateTime());
                }
            }
            return reader;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка сохранения читателя: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Reader> findById(Integer id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска читателя по ID", e);
        }
    }

    @Override
    public List<Reader> findAll() {
        List<Reader> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка получения списка читателей", e);
        }
    }

    @Override
    public boolean update(Reader reader) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {

            ps.setString(1, reader.getFullName());
            ps.setString(2, reader.getEmail());
            ps.setString(3, reader.getPhone());
            ps.setString(4, reader.getLibraryCardNumber());
            ps.setInt(5, reader.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка обновления читателя", e);
        }
    }

    @Override
    public boolean deleteById(Integer id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка удаления читателя", e);
        }
    }

    public boolean existsByEmail(String email) {
        return existsByString(EXISTS_EMAIL, email);
    }

    public boolean existsByCardNumber(String card) {
        return existsByString(EXISTS_CARD, card);
    }

    public boolean hasActiveRequests(int readerId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_ACTIVE_REQUESTS)) {
            ps.setInt(1, readerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка проверки активных заявок", e);
        }
    }

    private boolean existsByString(String sql, String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка проверки уникальности", e);
        }
    }

    private Reader mapRow(ResultSet rs) throws SQLException {
        return new Reader(
                rs.getInt("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("library_card_number"),
                rs.getTimestamp("registered_at").toLocalDateTime()
        );
    }
}