package ru.mirea.library.model;

/**
 * Статус заявки на выдачу книги.
 * Значения совпадают с CHECK-ограничением в таблице book_requests.
 */
public enum RequestStatus {

    CREATED,    // создана
    APPROVED,   // одобрена библиотекарем
    ISSUED,     // книга выдана
    RETURNED,   // книга возвращена
    CANCELLED,  // отменена
    REJECTED;   // отклонена

    /**
     * Проверяет, разрешён ли переход из текущего статуса в {@code next}.
     */
    public boolean canTransitionTo(RequestStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case CREATED -> next == APPROVED || next == CANCELLED || next == REJECTED;
            case APPROVED -> next == ISSUED || next == CANCELLED;
            case ISSUED -> next == RETURNED;
            case RETURNED, CANCELLED, REJECTED -> false;
        };
    }

    /**
     * Русское название статуса для вывода в консоль.
     */
    public String getRussianName() {
        return switch (this) {
            case CREATED -> "Создана";
            case APPROVED -> "Одобрена";
            case ISSUED -> "Выдана";
            case RETURNED -> "Возвращена";
            case CANCELLED -> "Отменена";
            case REJECTED -> "Отклонена";
        };
    }
}