package ru.mirea.library.ui;

import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.exception.DatabaseException;
import ru.mirea.library.exception.EntityNotFoundException;
import ru.mirea.library.model.Reader;
import ru.mirea.library.service.ReaderService;

import java.util.List;
import java.util.Scanner;

public class ReaderMenu {

    private final ReaderService service;
    private final Scanner scanner;

    public ReaderMenu(ReaderService service, Scanner scanner) {
        this.service = service;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            printMenu();
            int choice = InputHelper.readInt(scanner, "Выберите действие: ");
            try {
                switch (choice) {
                    case 1 -> create();
                    case 2 -> listAll();
                    case 3 -> findById();
                    case 4 -> update();
                    case 5 -> delete();
                    case 0 -> { return; }
                    default -> System.out.println("Неверный пункт меню.");
                }
            } catch (EntityNotFoundException | BusinessException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (DatabaseException e) {
                System.out.println("Ошибка БД: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("\nЧИТАТЕЛИ");
        System.out.println("1. Добавить читателя");
        System.out.println("2. Список всех читателей");
        System.out.println("3. Найти по ID");
        System.out.println("4. Изменить читателя");
        System.out.println("5. Удалить читателя");
        System.out.println("0. Назад");
    }

    private void create() {
        String name = InputHelper.readString(scanner, "ФИО: ");
        String email = InputHelper.readString(scanner, "Email: ");
        String phone = InputHelper.readString(scanner, "Телефон (можно пусто): ");
        String card  = InputHelper.readString(scanner, "Билет (LIB-XXXX): ");

        Reader r = service.create(name, email, phone.isBlank() ? null : phone, card);
        System.out.println("Создан читатель: " + r);
    }

    private void listAll() {
        List<Reader> readers = service.getAll();
        if (readers.isEmpty()) {
            System.out.println("Список пуст.");
            return;
        }
        readers.forEach(System.out::println);
    }

    private void findById() {
        int id = InputHelper.readInt(scanner, "ID: ");
        System.out.println(service.getById(id));
    }

    private void update() {
        int id = InputHelper.readInt(scanner, "ID изменяемого читателя: ");
        Reader current = service.getById(id);
        System.out.println("Текущее: " + current);

        String name = InputHelper.readString(scanner,
                "Новое ФИО [" + current.getFullName() + "]: ");
        if (name.isBlank()) name = current.getFullName();

        String email = InputHelper.readString(scanner,
                "Новый email [" + current.getEmail() + "]: ");
        if (email.isBlank()) email = current.getEmail();

        String phone = InputHelper.readString(scanner,
                "Новый телефон [" + current.getPhone() + "]: ");
        if (phone.isBlank()) phone = current.getPhone();

        String card = InputHelper.readString(scanner,
                "Новый билет [" + current.getLibraryCardNumber() + "]: ");
        if (card.isBlank()) card = current.getLibraryCardNumber();

        Reader updated = service.update(id, name, email, phone, card);
        System.out.println("Обновлено: " + updated);
    }

    private void delete() {
        int id = InputHelper.readInt(scanner, "ID для удаления: ");
        System.out.print("Подтвердите удаление (y/n): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("Отменено.");
            return;
        }
        service.delete(id);
        System.out.println("Читатель удалён.");
    }
}