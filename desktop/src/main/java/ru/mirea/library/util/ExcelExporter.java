package ru.mirea.library.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.RequestExportRow;
import ru.mirea.library.repository.ReportRepository;

import java.io.FileOutputStream;
import java.util.List;

public final class ExcelExporter {

    private ExcelExporter() { }

    private static final String[] HEADERS = {
            "ID заявки", "ФИО читателя", "Название книги", "Автор",
            "ISBN", "Статус", "Создана", "Вернуть до", "Комментарий"
    };

    public static void exportBookRequests(String filePath) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Заявки");

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle bodyStyle   = createBodyStyle(wb);
            CellStyle dateTimeStyle = createDateStyle(wb, "dd.MM.yyyy HH:mm");
            CellStyle dateStyle = createDateStyle(wb, "dd.MM.yyyy");

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            List<RequestExportRow> requests = new ReportRepository().requestsForExport();
            int rowIdx = 1;
            for (RequestExportRow request : requests) {
                Row row = sheet.createRow(rowIdx++);

                setCell(row, 0, request.id(), bodyStyle);
                setCell(row, 1, request.readerName(), bodyStyle);
                setCell(row, 2, request.bookTitle(), bodyStyle);
                setCell(row, 3, request.bookAuthor(), bodyStyle);
                setCell(row, 4, request.isbn(), bodyStyle);
                setCell(row, 5, request.status().getRussianName(), bodyStyle);

                Cell createdCell = row.createCell(6);
                if (request.createdAt() != null) {
                    createdCell.setCellValue(request.createdAt());
                    createdCell.setCellStyle(dateTimeStyle);
                } else {
                    createdCell.setCellValue("—");
                    createdCell.setCellStyle(bodyStyle);
                }

                Cell returnCell = row.createCell(7);
                if (request.desiredReturnDate() != null) {
                    returnCell.setCellValue(request.desiredReturnDate());
                    returnCell.setCellStyle(dateStyle);
                } else {
                    returnCell.setCellValue("—");
                    returnCell.setCellStyle(bodyStyle);
                }

                setCell(row, 8, request.comment(), bodyStyle);
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
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
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
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderTop(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);
        return style;
    }

    private static CellStyle createDateStyle(Workbook wb, String format) {
        CellStyle style = createBodyStyle(wb);
        CreationHelper helper = wb.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat(format));
        return style;
    }
}
