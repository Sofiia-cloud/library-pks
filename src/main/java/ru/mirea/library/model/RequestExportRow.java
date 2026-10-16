package ru.mirea.library.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Строка выгрузки заявки вместе с именем читателя. */
public record RequestExportRow(
        int id,
        String readerName,
        String bookTitle,
        String bookAuthor,
        String isbn,
        RequestStatus status,
        LocalDateTime createdAt,
        LocalDate desiredReturnDate,
        String comment) {
}
