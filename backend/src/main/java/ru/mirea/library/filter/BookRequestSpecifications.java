package ru.mirea.library.filter;

import org.springframework.data.jpa.domain.Specification;

import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.model.RequestStatus;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public final class BookRequestSpecifications {
    private BookRequestSpecifications() {}

    public static Specification<BookRequest> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<BookRequest> titleContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("bookTitle")), pattern(text), '!');
    }

    public static Specification<BookRequest> authorContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("bookAuthor")), pattern(text), '!');
    }

    public static Specification<BookRequest> readerNameContains(String text) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("reader").get("fullName")), pattern(text), '!');
    }

    public static Specification<BookRequest> readerCardEquals(String card) {
        return (root, query, cb) -> cb.equal(root.get("reader").get("libraryCardNumber"), card);
    }

    public static Specification<BookRequest> readerEquals(UUID readerId) {
        return (root, query, cb) -> cb.equal(root.get("reader").get("id"), readerId);
    }

    public static Specification<BookRequest> statusEquals(RequestStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<BookRequest> createdBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> cb.between(root.get("createdAt"), from, to);
    }

    public static Specification<BookRequest> orderByStatus(boolean ascending) {
        return (root, query, cb) -> {
            var order =
                    cb.<RequestStatus, Integer>selectCase(root.get("status"))
                            .when(RequestStatus.CREATED, 0)
                            .when(RequestStatus.APPROVED, 1)
                            .when(RequestStatus.ISSUED, 2)
                            .when(RequestStatus.RETURNED, 3)
                            .when(RequestStatus.CANCELLED, 4)
                            .when(RequestStatus.REJECTED, 5)
                            .otherwise(6);
            query.orderBy(
                    ascending ? cb.asc(order) : cb.desc(order), cb.asc(root.get("requestNumber")));
            return cb.conjunction();
        };
    }

    private static String pattern(String text) {
        return "%"
                + text.toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                + "%";
    }
}
