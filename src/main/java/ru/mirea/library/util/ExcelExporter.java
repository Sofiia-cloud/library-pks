package ru.mirea.library.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.library.exception.DatabaseException;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class ExcelExporter {

    private ExcelExporter() { }

    private static final String SQL = """
            SELECT br.id,
                   r.full_name            AS reader_name,
                   br.book_title,
                   br.book_author,
                   br.isbn,
                   br.status,
                   br.created_at,
                   br.desired_return_date,
                   br.comment
            FROM book_requests br
            JOIN readers r ON r.id = br.reader_id
            ORDER BY br.id
            """;

    private static final String[] HEADERS = {
            "ID заявки", "ФИО читателя", "Название книги", "Автор",
            "ISBN", "Статус", "Создана", "Вернуть до", "Комментарий"
    };

    public static void exportBookRequests(String filePath) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Заявки");

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle bodyStyle   = createBodyStyle(wb);
            CellStyle dateStyle   = createDateStyle(wb);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL);
                 ResultSet rs = ps.executeQuery()) {

                int rowIdx = 1;
                while (rs.next()) {
                    Row row = sheet.createRow(rowIdx++);

                    setCell(row, 0, rs.getInt("id"), bodyStyle);
                    setCell(row, 1, rs.getString("reader_name"), bodyStyle);
                    setCell(row, 2, rs.getString("book_title"), bodyStyle);
                    setCell(row, 3, rs.getString("book_author"), bodyStyle);
                    setCell(row, 4, rs.getString("isbn"), bodyStyle);
                    setCell(row, 5, rs.getString("status"), bodyStyle);

                    Cell createdCell = row.createCell(6);
                    if (rs.getTimestamp("created_at") != null) {
                        createdCell.setCellValue(rs.getTimestamp("created_at").toLocalDateTime());
                        createdCell.setCellStyle(dateStyle);
                    } else {
                        createdCell.setCellValue("—");
                        createdCell.setCellStyle(bodyStyle);
                    }

                    Cell returnCell = row.createCell(7);
                    if (rs.getDate("desired_return_date") != null) {
                        returnCell.setCellValue(rs.getDate("desired_return_date").toLocalDate());
                        returnCell.setCellStyle(dateStyle);
                    } else {
                        returnCell.setCellValue("—");
                        returnCell.setCellStyle(bodyStyle);
                    }

                    setCell(row, 8, rs.getString("comment"), bodyStyle);
                }
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                wb.write(out);
            }

        } catch (Exception e) {
            throw new DatabaseException("Ошибка экспорта в Excel: " + e.getMessage(), e);
        }
    }

    private static void setCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("—");
        } else {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createBodyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderTop(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);
        return style;
    }

    private static CellStyle createDateStyle(Workbook wb) {
        CellStyle style = createBodyStyle(wb);
        CreationHelper helper = wb.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("dd.MM.yyyy HH:mm"));
        return style;
    }
}