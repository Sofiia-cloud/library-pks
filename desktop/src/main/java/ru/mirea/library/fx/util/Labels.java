package ru.mirea.library.fx.util;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import ru.mirea.library.model.*;

public final class Labels {
    private Labels() {}

    public static String text(Object value) {
        if (value == null) return "Все";
        if (value instanceof RequestStatus s) return s.getRussianName();
        return switch (value.toString()) {
            case "ACTIVE" -> "Активные";
            case "ARCHIVED" -> "Архив";
            case "ALL" -> "Все";
            case "NOT_ISSUED" -> "Книга не выдана";
            case "NO_DUE_DATE" -> "Без срока возврата";
            case "ON_TIME" -> "Срок не скоро";
            case "DUE_SOON" -> "Вернуть в течение 3 дней";
            case "OVERDUE" -> "Просрочено";
            case "CLOSED" -> "Завершено";
            case "EQ" -> "=";
            case "NE" -> "≠";
            case "GT" -> ">";
            case "GE" -> "≥";
            case "LT" -> "<";
            case "LE" -> "≤";
            case "NUMBER" -> "Номер";
            case "TITLE" -> "Название";
            case "CREATED_AT" -> "Создание";
            case "STATUS" -> "Статус";
            case "ARCHIVE" -> "В архив";
            case "RESTORE" -> "Восстановить";
            case "ADD_TAG" -> "Добавить тег";
            case "REMOVE_TAG" -> "Убрать тег";
            case "NEW_ONLY" -> "Только новые";
            case "UPSERT" -> "Обновление по коду";
            case "VIEWER" -> "Просмотр";
            case "LIBRARIAN" -> "Библиотекарь";
            case "ADMIN" -> "Администратор";
            default -> value.toString();
        };
    }

    public static <T> void configure(ComboBox<T> box) {
        box.setConverter(
                new StringConverter<T>() {
                    public String toString(T value) {
                        return text(value);
                    }

                    public T fromString(String value) {
                        return null;
                    }
                });
    }
}
