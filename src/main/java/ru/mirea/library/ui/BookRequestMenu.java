package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.service.BookRequestService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BookRequestMenu {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BookRequestService service;

    public BookRequestMenu(BookRequestService service) {
        this.service = service;
    }

    public void show() {
        while (true) {
            printMenu();
            int choice = InputHelper.readInt("Выберите пункт: ");
            try {
                switch (choice) {
                    case 1 -> create();
                    case 2 -> listAll();
                    case 3 -> findById();
                    case 4 -> update();
                    case 5 -> delete();
                    case 6 -> changeStatus();
                    case 0 -> { return; }
                    default -> System.out.println("Нет такого пункта.");
                }
            } catch (BusinessException | EntityNotFoundException | DatabaseException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("========= ЗАЯВКИ НА ВЫДАЧУ КНИГИ =========");
        System.out.println("1. Создать заявку");
        System.out.println("2. Список всех заявок");
        System.out.println("3. Найти по ID");
        System.out.println("4. Изменить заявку");
        System.out.println("5. Удалить заявку");
        System.out.println("6. Изменить статус");
        System.out.println("0. Назад");
        System.out.println("==========================================");
    }

    private void create() {
        int readerId = InputHelper.readInt("ID читателя: ");
        String title = InputHelper.readString("Название книги: ");
        String author = InputHelper.readString("Автор книги: ");
        String isbn = InputHelper.readString("ISBN (Enter — пропустить): ");
        LocalDate desired = readOptionalDate("Дата возврата (дд.мм.гггг, Enter — пропустить): ");
        String comment = InputHelper.readString("Комментарий (Enter — пропустить): ");

        BookRequest created = service.create(readerId, title, author, isbn, desired, comment);
        System.out.println("Заявка создана: " + created);
    }

    private void listAll() {
        List<BookRequest> all = service.getAll();
        if (all.isEmpty()) {
            System.out.println("Заявок нет.");
            return;
        }
        all.forEach(System.out::println);
        System.out.println("Всего: " + all.size());
    }

    private void findById() {
        int id = InputHelper.readInt("ID заявки: ");
        BookRequest r = service.getById(id);
        System.out.println(r);
    }

    private void update() {
        int id = InputHelper.readInt("ID заявки для изменения: ");
        BookRequest existing = service.getById(id);
        System.out.println("Текущая: " + existing);

        String title = InputHelper.readString("Новое название книги: ");
        String author = InputHelper.readString("Новый автор: ");
        String isbn = InputHelper.readString("Новый ISBN (Enter — пропустить): ");
        LocalDate desired = readOptionalDate("Новая дата возврата (дд.мм.гггг, Enter — пропустить): ");
        String comment = InputHelper.readString("Новый комментарий (Enter — пропустить): ");

        BookRequest updated = service.update(id, title, author, isbn, desired, comment);
        System.out.println("Обновлено: " + updated);
    }

    private void delete() {
        int id = InputHelper.readInt("ID заявки для удаления: ");
        BookRequest existing = service.getById(id);
        System.out.println("Будет удалено: " + existing);
        String confirm = InputHelper.readString("Подтвердить удаление (y/n): ");
        if (!"y".equalsIgnoreCase(confirm)) {
            System.out.println("Отменено.");
            return;
        }
        service.delete(id);
        System.out.println("Удалено.");
    }

    private void changeStatus() {
        int id = InputHelper.readInt("ID заявки: ");
        BookRequest existing = service.getById(id);
        System.out.println("Текущий статус: " + existing.getStatus().getRussianName());

        System.out.println("Доступные переходы:");
        for (RequestStatus s : RequestStatus.values()) {
            if (existing.getStatus().canTransitionTo(s)) {
                System.out.println("  " + s + " (" + s.getRussianName() + ")");
            }
        }

        String input = InputHelper.readString("Новый статус (например, APPROVED): ");
        RequestStatus newStatus;
        try {
            newStatus = RequestStatus.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Неизвестный статус: " + input);
        }

        BookRequest updated = service.changeStatus(id, newStatus);
        System.out.println("Статус изменён: " + updated.getStatus().getRussianName());
    }

    private LocalDate readOptionalDate(String prompt) {
        String raw = InputHelper.readString(prompt);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FMT);
        } catch (Exception e) {
            throw new BusinessException("Неверный формат даты: " + raw);
        }
    }
}