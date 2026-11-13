package ru.mirea.library;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ru.mirea.library.model.*;

import java.time.LocalDate;

class ReturnStateTest {
    @Test
    void computesReturnStateAtDateBoundaries() {
        LocalDate today = LocalDate.of(2026, 11, 13);
        assertEquals(
                ReturnState.NOT_ISSUED,
                ReturnState.calculate(RequestStatus.APPROVED, today.minusDays(2), today));
        assertEquals(
                ReturnState.NO_DUE_DATE, ReturnState.calculate(RequestStatus.ISSUED, null, today));
        assertEquals(
                ReturnState.OVERDUE,
                ReturnState.calculate(RequestStatus.ISSUED, today.minusDays(1), today));
        assertEquals(
                ReturnState.DUE_SOON, ReturnState.calculate(RequestStatus.ISSUED, today, today));
        assertEquals(
                ReturnState.DUE_SOON,
                ReturnState.calculate(RequestStatus.ISSUED, today.plusDays(3), today));
        assertEquals(
                ReturnState.ON_TIME,
                ReturnState.calculate(RequestStatus.ISSUED, today.plusDays(4), today));
        assertEquals(
                ReturnState.CLOSED,
                ReturnState.calculate(RequestStatus.RETURNED, today.minusDays(5), today));
    }
}
