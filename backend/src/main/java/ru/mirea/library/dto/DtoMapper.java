package ru.mirea.library.dto;

import ru.mirea.library.domain.*;

public final class DtoMapper {
    private DtoMapper() {}

    public static ReaderDto reader(Reader r) {
        return new ReaderDto(
                r.getId(),
                r.getReaderNumber(),
                r.getFullName(),
                r.getEmail(),
                r.getPhone(),
                r.getLibraryCardNumber(),
                r.getRegisteredAt(),
                r.isActive(),
                r.getUpdatedAt(),
                r.getVersion());
    }

    public static BookRequestDto request(BookRequest b) {
        return new BookRequestDto(
                b.getId(),
                b.getRequestNumber(),
                b.getReader().getId(),
                b.getReader().getFullName(),
                b.getBookTitle(),
                b.getBookAuthor(),
                b.getIsbn(),
                b.getStatus(),
                b.getCreatedAt(),
                b.getDesiredReturnDate(),
                b.getComment(),
                b.getRequestedCopies(),
                b.isUrgent(),
                b.isArchived(),
                b.getUpdatedAt(),
                b.getVersion());
    }
}
