package ru.mirea.library.model;

public enum ReturnState {
    NOT_ISSUED,
    NO_DUE_DATE,
    ON_TIME,
    DUE_SOON,
    OVERDUE,
    CLOSED;

    public static ReturnState calculate(
            RequestStatus status, java.time.LocalDate due, java.time.LocalDate today) {
        if (status == RequestStatus.RETURNED
                || status == RequestStatus.CANCELLED
                || status == RequestStatus.REJECTED) return CLOSED;
        if (status != RequestStatus.ISSUED) return NOT_ISSUED;
        if (due == null) return NO_DUE_DATE;
        if (due.isBefore(today)) return OVERDUE;
        return due.isAfter(today.plusDays(3)) ? ON_TIME : DUE_SOON;
    }
}
