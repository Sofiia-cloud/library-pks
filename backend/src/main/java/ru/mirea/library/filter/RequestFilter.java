package ru.mirea.library.filter;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import ru.mirea.library.domain.BookRequest;
import ru.mirea.library.error.BusinessException;
import ru.mirea.library.model.*;

import java.time.*;
import java.util.*;

public class RequestFilter {
    private String q;
    private SearchField field;
    private UUID readerId;
    private RequestStatus status;
    private Boolean urgent;
    private String tag;
    private ArchiveScope scope = ArchiveScope.ACTIVE;
    private ReturnState returnState;
    private CopiesComparison copiesOp;
    private Integer copiesValue;
    @org.springframework.format.annotation.DateTimeFormat(
            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from, to;
    private RequestSort sort = RequestSort.NUMBER;
    private boolean ascending = true;

    public Specification<BookRequest> specification() {
        Specification<BookRequest> s = BookRequestSpecifications.all();
        if (q != null && !q.isBlank()) {
            if (q.length() > 200) throw new BusinessException("Поиск: не более 200 символов");
            String t = q.trim();
            s =
                    s.and(
                            field == null
                                    ? BookRequestSpecifications.titleContains(t)
                                            .or(BookRequestSpecifications.authorContains(t))
                                            .or(BookRequestSpecifications.readerNameContains(t))
                                            .or(BookRequestSpecifications.readerCardEquals(t))
                                    : switch (field) {
                                        case TITLE -> BookRequestSpecifications.titleContains(t);
                                        case AUTHOR -> BookRequestSpecifications.authorContains(t);
                                        case READER_NAME ->
                                                BookRequestSpecifications.readerNameContains(t);
                                        case CARD -> BookRequestSpecifications.readerCardEquals(t);
                                    });
        }
        if (readerId != null) s = s.and(BookRequestSpecifications.readerEquals(readerId));
        if (status != null) s = s.and(BookRequestSpecifications.statusEquals(status));
        if (scope != ArchiveScope.ALL)
            s = s.and((r, z, c) -> c.equal(r.get("archived"), scope == ArchiveScope.ARCHIVED));
        if (urgent != null) s = s.and((r, z, c) -> c.equal(r.get("urgent"), urgent));
        if (tag != null && !tag.isBlank()) {
            String t = tag.trim().toLowerCase(Locale.ROOT);
            s =
                    s.and(
                            (r, z, c) -> {
                                var sub = z.subquery(UUID.class);
                                var item = sub.from(BookRequest.class);
                                sub.select(item.get("id"))
                                        .where(c.equal(item.join("tags").get("name"), t));
                                return r.get("id").in(sub);
                            });
        }
        if (from != null || to != null) {
            if (from == null || to == null || from.isAfter(to))
                throw new BusinessException("Укажите правильный диапазон дат");
            s = s.and(BookRequestSpecifications.createdBetween(from, to));
        }
        if ((copiesOp == null) != (copiesValue == null))
            throw new BusinessException("Укажите операцию и количество вместе");
        if (copiesOp != null)
            s =
                    s.and(
                            (r, z, c) ->
                                    switch (copiesOp) {
                                        case EQ -> c.equal(r.get("requestedCopies"), copiesValue);
                                        case NE ->
                                                c.notEqual(r.get("requestedCopies"), copiesValue);
                                        case GT -> c.gt(r.get("requestedCopies"), copiesValue);
                                        case GE -> c.ge(r.get("requestedCopies"), copiesValue);
                                        case LT -> c.lt(r.get("requestedCopies"), copiesValue);
                                        case LE -> c.le(r.get("requestedCopies"), copiesValue);
                                    });
        if (returnState != null) s = s.and(state(returnState));
        if (sort == RequestSort.STATUS)
            s = s.and(BookRequestSpecifications.orderByStatus(ascending));
        return s;
    }

    public static Specification<BookRequest> state(ReturnState state) {
        LocalDate today = LocalDate.now();
        return (r, q, c) -> {
            var status = r.get("status");
            var due = r.<LocalDate>get("desiredReturnDate");
            var closed =
                    status.in(
                            RequestStatus.RETURNED,
                            RequestStatus.CANCELLED,
                            RequestStatus.REJECTED);
            var issued = c.equal(status, RequestStatus.ISSUED);
            return switch (state) {
                case CLOSED -> closed;
                case NOT_ISSUED -> c.and(c.not(closed), c.not(issued));
                case NO_DUE_DATE -> c.and(issued, c.isNull(due));
                case OVERDUE -> c.and(issued, c.lessThan(due, today));
                case DUE_SOON -> c.and(issued, c.between(due, today, today.plusDays(3)));
                case ON_TIME -> c.and(issued, c.greaterThan(due, today.plusDays(3)));
            };
        };
    }

    public Sort ordering() {
        if (sort == RequestSort.STATUS) return Sort.unsorted();
        String p =
                switch (sort) {
                    case TITLE -> "bookTitle";
                    case CREATED_AT -> "createdAt";
                    case STATUS -> "status";
                    default -> "requestNumber";
                };
        var o = new Sort.Order(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, p);
        if (sort == RequestSort.TITLE) o = o.ignoreCase();
        return Sort.by(o).and(Sort.by("id"));
    }

    public String getQ() {
        return q;
    }

    public void setQ(String v) {
        q = v;
    }

    public SearchField getField() {
        return field;
    }

    public void setField(SearchField v) {
        field = v;
    }

    public UUID getReaderId() {
        return readerId;
    }

    public void setReaderId(UUID v) {
        readerId = v;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus v) {
        status = v;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean v) {
        urgent = v;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String v) {
        tag = v;
    }

    public ArchiveScope getScope() {
        return scope;
    }

    public void setScope(ArchiveScope v) {
        scope = v;
    }

    public ReturnState getReturnState() {
        return returnState;
    }

    public void setReturnState(ReturnState v) {
        returnState = v;
    }

    public CopiesComparison getCopiesOp() {
        return copiesOp;
    }

    public void setCopiesOp(CopiesComparison v) {
        copiesOp = v;
    }

    public Integer getCopiesValue() {
        return copiesValue;
    }

    public void setCopiesValue(Integer v) {
        copiesValue = v;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime v) {
        from = v;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime v) {
        to = v;
    }

    public RequestSort getSort() {
        return sort;
    }

    public void setSort(RequestSort v) {
        sort = v;
    }

    public boolean getAscending() {
        return ascending;
    }

    public void setAscending(boolean v) {
        ascending = v;
    }
}
