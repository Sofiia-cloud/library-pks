package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.service.FilterService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FilterMenu {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final FilterService service;

    public FilterMenu(FilterService service) {
        this.service = service;
    }

    public void show() {
        while (true) {
            System.out.println("\n===== ФИЛЬТРАЦИЯ ЗАЯВОК =====");
            System.out.println("1. По статусу");
            System.out.println("2. По диапазону дат");
            System.out.println("3. По читателю (ID)");
            System.out.println("0. Назад");
            int c = InputHelper.readInt("Выбор: ");
            try {
                switch (c) {
                    case 1 -> byStatus();
                    case 2 -> byDateRange();
                    case 3 -> byReader();
                    case 0 -> { return; }
                    default -> System.out.println("Нет такого пункта.");
                }
            } catch (BusinessException | DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private void byStatus() {
        System.out.print("Доступные: ");
        for (RequestStatus s : RequestStatus.values()) System.out.print(s + " ");
        System.out.println();
        String raw = InputHelper.readString("Статус: ").toUpperCase();
        RequestStatus st;
        try {
            st = RequestStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Неизвестный статус: " + raw);
        }
        print(service.byStatus(st));
    }

    private void byDateRange() {
        LocalDate from = readDate("Дата от (дд.мм.гггг): ");
        LocalDate to   = readDate("Дата до (дд.мм.гггг): ");
        List<BookRequest> list = service.byDateRange(
                from.atStartOfDay(),
                to.atTime(23, 59, 59));
        print(list);
    }

    private void byReader() {
        int id = InputHelper.readInt("ID читателя: ");
        print(service.byReader(id));
    }

    private LocalDate readDate(String prompt) {
        String raw = InputHelper.readString(prompt);
        try {
            return LocalDate.parse(raw.trim(), FMT);
        } catch (Exception e) {
            throw new BusinessException("Неверный формат даты: " + raw);
        }
    }

    private void print(List<BookRequest> list) {
        if (list.isEmpty()) { System.out.println("Ничего не найдено."); return; }
        list.forEach(System.out::println);
        System.out.println("Всего: " + list.size());
    }
}