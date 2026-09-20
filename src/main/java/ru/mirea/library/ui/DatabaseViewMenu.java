package ru.mirea.library.ui;

import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.util.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class DatabaseViewMenu {

    public void show() {
        while (true) {
            System.out.println();
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("  ТАБЛИЦЫ БАЗЫ ДАННЫХ");
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("  1. readers");
            System.out.println("  2. book_requests");
            System.out.println("  0. Назад");
            System.out.println("════════════════════════════════════════════════════════════");

            int c = InputHelper.readInt("  Выбор: ");
            try {
                switch (c) {
                    case 1 -> printTable("readers");
                    case 2 -> printTable("book_requests");
                    case 0 -> { return; }
                    default -> System.out.println("  Нет такого пункта.");
                }
            } catch (DatabaseException e) {
                System.out.println("  Ошибка БД: " + e.getMessage());
            }
        }
    }

    private void printTable(String table) {
        if (!table.equals("readers") && !table.equals("book_requests")) {
            throw new DatabaseException("Недопустимая таблица: " + table);
        }
        String sql = "SELECT * FROM " + table;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                header.append(String.format("%-22s", meta.getColumnName(i)));
            }
            System.out.println();
            System.out.println(header);
            System.out.println("─".repeat(cols * 22));

            int rows = 0;
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    String v = rs.getString(i);
                    if (v == null) v = "—";
                    if (v.length() > 20) v = v.substring(0, 19) + "…";
                    row.append(String.format("%-22s", v));
                }
                System.out.println(row);
                rows++;
            }
            System.out.println();
            System.out.println("  Всего записей: " + rows);

        } catch (Exception e) {
            throw new DatabaseException("Ошибка чтения таблицы " + table + ": " + e.getMessage(), e);
        }
    }
}