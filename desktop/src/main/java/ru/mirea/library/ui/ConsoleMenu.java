package ru.mirea.library.ui;

import com.fasterxml.jackson.core.type.TypeReference;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.model.*;
import ru.mirea.library.util.ExcelExporter;

import java.time.*;
import java.util.*;

public class ConsoleMenu {
    private final ApiClient api = ClientContext.api();
    private final ReaderApiClient readers = new ReaderApiClient(api);
    private final BookRequestApiClient requests = new BookRequestApiClient(api);

    public void show() {
        try {
            while (true) {
                if (api.user() == null) {
                    login();
                    continue;
                }
                if (api.user().mustChangePassword()) {
                    password();
                    continue;
                }
                System.out.println(
                        "\nБИБЛИОТЕКА — " + api.user().name() + " " + api.user().roles());
                System.out.println(
                        "1. Читатели  2. Заявки  3. Поиск  4. Фильтры  5. Сортировка\n"
                                + "6. Статистика  7. Отчёты  8. Просмотр данных  9. Экспорт Excel\n"
                                + "10. Сменить пароль  11. Выйти из учётной записи  0. Завершить");
                try {
                    switch (InputHelper.readInt("Выбор: ")) {
                        case 1 -> readerMenu();
                        case 2 -> requestMenu();
                        case 3 -> search();
                        case 4 -> filter();
                        case 5 -> sort();
                        case 6 ->
                                System.out.println(
                                        api.get("/api/reports/statistics", StatisticsDto.class));
                        case 7 -> reports();
                        case 8 -> {
                            System.out.println("Читатели:");
                            readers.list().forEach(System.out::println);
                            System.out.println("Заявки:");
                            requests.list().forEach(System.out::println);
                        }
                        case 9 -> {
                            String file = InputHelper.readString("Файл .xlsx: ");
                            ExcelExporter.exportBookRequests(file);
                            System.out.println("Экспорт сохранён");
                        }
                        case 10 -> password();
                        case 11 -> api.logout();
                        case 0 -> {
                            return;
                        }
                        default -> System.out.println("Нет такого пункта");
                    }
                } catch (ApiException e) {
                    System.out.println(e.getMessage());
                    if (e.status() == 401) api.clearSession();
                } catch (IllegalArgumentException e) {
                    System.out.println("Некорректный ввод: " + e.getMessage());
                }
            }
        } finally {
            try {
                api.logout();
            } catch (ApiException ignored) {
                api.clearSession();
            }
        }
    }

    private void login() {
        String login = InputHelper.readString("Логин: "),
                password = InputHelper.readPassword("Пароль: ");
        try {
            api.login(login, password);
        } catch (ApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void password() {
        String current = InputHelper.readPassword("Текущий пароль: "),
                replacement = InputHelper.readPassword("Новый пароль: ");
        String confirmation = InputHelper.readPassword("Повторите новый пароль: ");
        if (!replacement.equals(confirmation)) {
            System.out.println("Пароли не совпадают");
            return;
        }
        try {
            api.changePassword(current, replacement);
            System.out.println("Пароль изменён. Войдите снова");
        } catch (ApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void readerMenu() {
        System.out.println(
                "1. Список  2. Найти по UUID  3. Создать  4. Изменить  5. Включить / отключить");
        switch (InputHelper.readInt("Выбор: ")) {
            case 1 -> readers.list().forEach(System.out::println);
            case 2 -> System.out.println(readers.get(uuid("UUID читателя: ")));
            case 3 -> System.out.println(readers.create(readerCreate()));
            case 4 -> {
                ReaderDto reader = readers.get(uuid("UUID читателя: "));
                System.out.println(reader);
                ReaderCreate fields = readerCreate();
                System.out.println(
                        readers.update(
                                reader.id(),
                                new ReaderUpdate(
                                        reader.version(),
                                        fields.fullName(),
                                        fields.email(),
                                        fields.phone(),
                                        fields.libraryCardNumber())));
            }
            case 5 -> {
                ReaderDto reader = readers.get(uuid("UUID читателя: "));
                if (InputHelper.readYesNo(
                        (reader.active() ? "Отключить " : "Включить ") + reader.fullName() + "? "))
                    api.put(
                            "/api/readers/" + reader.id() + "/active",
                            new ActiveUpdate(reader.version(), !reader.active()),
                            ReaderDto.class);
            }
            default -> {}
        }
    }

    private ReaderCreate readerCreate() {
        return new ReaderCreate(
                InputHelper.readString("ФИО: "),
                InputHelper.readString("Email: "),
                InputHelper.readString("Телефон: "),
                InputHelper.readString("Билет LIB-XXXX: "));
    }

    private void requestMenu() {
        System.out.println(
                "1. Список  2. Найти по UUID  3. Создать  4. Изменить  5. В архив  6. Сменить"
                        + " статус  7. Архив  8. Восстановить  9. Удалить навсегда");
        switch (InputHelper.readInt("Выбор: ")) {
            case 1 -> requests.list().forEach(System.out::println);
            case 2 -> System.out.println(requests.get(uuid("UUID заявки: ")));
            case 3 -> {
                UUID reader = uuid("UUID читателя: ");
                var fields = requestFields(reader);
                System.out.println(requests.create(fields));
            }
            case 4 -> {
                BookRequestDto old = requests.get(uuid("UUID заявки: "));
                System.out.println(old);
                var fields = requestFields(old.readerId());
                System.out.println(
                        requests.update(
                                old.id(),
                                new BookRequestUpdate(
                                        old.version(),
                                        fields.bookTitle(),
                                        fields.bookAuthor(),
                                        fields.isbn(),
                                        fields.desiredReturnDate(),
                                        fields.comment(),
                                        fields.status(),
                                        fields.requestedCopies(),
                                        fields.urgent(),
                                        fields.tags())));
            }
            case 5 -> {
                BookRequestDto old = requests.get(uuid("UUID заявки: "));
                if (InputHelper.readYesNo("Архивировать заявку №" + old.requestNumber() + "? "))
                    requests.delete(old);
            }
            case 6 -> {
                BookRequestDto old = requests.get(uuid("UUID заявки: "));
                System.out.println("Сейчас: " + old.status());
                System.out.println(requests.status(old, status()));
            }
            case 7 ->
                    requests.list(Map.of("scope", ArchiveScope.ARCHIVED))
                            .forEach(System.out::println);
            case 8 -> {
                var old = requests.get(uuid("UUID заявки: "));
                System.out.println(
                        api.post(
                                "/api/book-requests/" + old.id() + "/restore",
                                new VersionRequest(old.version()),
                                BookRequestDto.class));
            }
            case 9 -> {
                var old = requests.get(uuid("UUID заявки: "));
                if (InputHelper.readYesNo("Окончательно удалить архивную заявку? "))
                    api.delete(
                            "/api/book-requests/"
                                    + old.id()
                                    + "/permanent?version="
                                    + old.version());
            }
            default -> {}
        }
    }

    private BookRequestCreate requestFields(UUID reader) {
        return new BookRequestCreate(
                reader,
                InputHelper.readString("Название: "),
                InputHelper.readString("Автор: "),
                InputHelper.readString("ISBN: "),
                InputHelper.readOptionalDate("Вернуть до (дд.мм.гггг, Enter — без даты): "),
                InputHelper.readString("Комментарий: "),
                status(),
                InputHelper.readInt("Количество экземпляров (1–1000): "),
                InputHelper.readYesNo("Срочная заявка? "),
                Arrays.stream(InputHelper.readString("Теги через запятую: ").split(","))
                        .map(String::trim)
                        .filter(t -> !t.isBlank())
                        .collect(java.util.stream.Collectors.toSet()),
                null);
    }

    private void search() {
        System.out.println("Поля: TITLE, AUTHOR, READER_NAME, CARD");
        SearchField field =
                SearchField.valueOf(InputHelper.readString("Поле: ").toUpperCase(Locale.ROOT));
        requests.list(Map.of("field", field, "q", InputHelper.readString("Текст: ")))
                .forEach(System.out::println);
    }

    private void filter() {
        System.out.println("1. Статус  2. Читатель  3. Даты создания");
        switch (InputHelper.readInt("Выбор: ")) {
            case 1 -> requests.list(Map.of("status", status())).forEach(System.out::println);
            case 2 ->
                    requests.list(Map.of("readerId", uuid("UUID читателя: ")))
                            .forEach(System.out::println);
            case 3 ->
                    requests.list(
                                    Map.of(
                                            "from",
                                            InputHelper.readDate("С (дд.мм.гггг): ").atStartOfDay(),
                                            "to",
                                            InputHelper.readDate("По (дд.мм.гггг): ")
                                                    .atTime(LocalTime.MAX)))
                            .forEach(System.out::println);
            default -> {}
        }
    }

    private void sort() {
        RequestSort sort =
                RequestSort.valueOf(
                        InputHelper.readString("Поле NUMBER, CREATED_AT, TITLE, STATUS: ")
                                .toUpperCase(Locale.ROOT));
        requests.list(Map.of("sort", sort, "ascending", InputHelper.readYesNo("По возрастанию? ")))
                .forEach(System.out::println);
    }

    private void reports() {
        System.out.println(
                "1. Сводка  2. По статусам  3. Топ книг  4. Топ авторов  5. Топ читателей\n"
                        + "6. Активность читателей  7. Читатели без заявок  8. По месяцам");
        switch (InputHelper.readInt("Выбор: ")) {
            case 1 ->
                    System.out.println(
                            api.get(
                                    "/api/reports/summary",
                                    new TypeReference<Map<String, Long>>() {}));
            case 2 ->
                    System.out.println(
                            api.get("/api/reports/statistics", StatisticsDto.class).statusCounts());
            case 3 ->
                    api.getList(
                                    "/api/reports/top-books?limit="
                                            + InputHelper.readInt("Сколько: "),
                                    NameCountDto.class)
                            .forEach(System.out::println);
            case 4 ->
                    api.getList(
                                    "/api/reports/top-authors?limit="
                                            + InputHelper.readInt("Сколько: "),
                                    NameCountDto.class)
                            .forEach(System.out::println);
            case 5 ->
                    api.getList(
                                    "/api/reports/top-readers?limit="
                                            + InputHelper.readInt("Сколько: "),
                                    ReaderActivityDto.class)
                            .forEach(System.out::println);
            case 6 ->
                    api.getList("/api/reports/reader-activity", ReaderActivityDto.class)
                            .forEach(System.out::println);
            case 7 ->
                    api.getList("/api/reports/readers-without-requests", ReaderDto.class)
                            .forEach(System.out::println);
            case 8 ->
                    System.out.println(
                            api.get(
                                    "/api/reports/by-month?year=" + InputHelper.readInt("Год: "),
                                    new TypeReference<Map<Integer, Long>>() {}));
            default -> {}
        }
    }

    private UUID uuid(String prompt) {
        return UUID.fromString(InputHelper.readString(prompt));
    }

    private RequestStatus status() {
        return RequestStatus.valueOf(
                InputHelper.readString(
                                "Статус CREATED, APPROVED, ISSUED, RETURNED, CANCELLED, REJECTED: ")
                        .toUpperCase(Locale.ROOT));
    }
}
