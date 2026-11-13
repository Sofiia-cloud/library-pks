package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.*;
import ru.mirea.library.model.*;

import java.util.*;

public class UsersController {
    @FXML private VBox view;
    private final TableView<AdminUserDto> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label info = new Label();
    private final Button previous = new Button("Назад"), next = new Button("Далее");
    private final ApiClient api = ClientContext.api();
    private int page;

    @FXML
    private void initialize() {
        column("Логин", AdminUserDto::login);
        column("Имя", AdminUserDto::name);
        column(
                "Роли",
                u ->
                        u.roles().stream()
                                .map(Labels::text)
                                .sorted()
                                .collect(java.util.stream.Collectors.joining(", ")));
        column("Активен", u -> u.active() ? "Да" : "Нет");
        column("Смена пароля", u -> u.mustChangePassword() ? "Обязательна" : "Нет");
        search.setPromptText("Поиск по логину и имени");
        var find = new Button("Найти");
        find.setOnAction(
                e -> {
                    page = 0;
                    load();
                });
        var create = new Button("Создать");
        create.setOnAction(e -> form(null));
        var edit = new Button("Изменить");
        edit.setOnAction(
                e -> {
                    var u = selected();
                    if (u != null) form(u);
                });
        var reset = new Button("Сбросить пароль");
        reset.setOnAction(e -> reset());
        previous.setOnAction(
                e -> {
                    page--;
                    load();
                });
        next.setOnAction(
                e -> {
                    page++;
                    load();
                });
        view.getChildren()
                .setAll(
                        new Label("Пользователи"),
                        new FlowPane(8, 8, search, find, create, edit, reset),
                        table,
                        new HBox(10, previous, info, next));
        VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    private void column(String title, java.util.function.Function<AdminUserDto, String> value) {
        var c = new TableColumn<AdminUserDto, String>(title);
        c.setCellValueFactory(
                v -> new javafx.beans.property.SimpleStringProperty(value.apply(v.getValue())));
        c.setPrefWidth(160);
        c.setSortable(false);
        table.getColumns().add(c);
    }

    private AdminUserDto selected() {
        var u = table.getSelectionModel().getSelectedItem();
        if (u == null) AlertHelper.showError("Выберите пользователя");
        return u;
    }

    private void load() {
        String q = search.getText();
        int currentPage = page;
        FxTasks.run(
                view,
                () ->
                        api.get(
                                "/api/admin/users"
                                        + ApiClient.query(
                                                Map.of("q", q, "page", currentPage, "size", 25)),
                                new com.fasterxml.jackson.core.type.TypeReference<
                                        PageDto<AdminUserDto>>() {}),
                p -> {
                    if (page > 0 && page >= p.totalPages()) {
                        page--;
                        load();
                        return;
                    }
                    table.getItems().setAll(p.items());
                    info.setText(
                            (p.page() + 1)
                                    + " / "
                                    + Math.max(1, p.totalPages())
                                    + " · Всего: "
                                    + p.totalElements());
                    previous.setDisable(page == 0);
                    next.setDisable(page + 1 >= p.totalPages());
                });
    }

    private void form(AdminUserDto u) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(view.getScene().getWindow());
        d.setTitle(u == null ? "Новый пользователь" : "Изменение пользователя");
        TextField login = new TextField(u == null ? "" : u.login()),
                name = new TextField(u == null ? "" : u.name());
        var active = new CheckBox("Учётная запись активна");
        active.setSelected(u == null || u.active());
        var roles = new EnumMap<Role, CheckBox>(Role.class);
        var box =
                new VBox(
                        10,
                        new Label("Логин (латиница, цифры, . _ @ -)"),
                        login,
                        new Label("Имя"),
                        name);
        for (Role role : Role.values()) {
            var c = new CheckBox(Labels.text(role));
            c.setSelected(u == null ? role == Role.VIEWER : u.roles().contains(role));
            roles.put(role, c);
            box.getChildren().add(c);
        }
        if (u != null) box.getChildren().add(active);
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait()
                .filter(b -> b == ButtonType.OK)
                .ifPresent(
                        button -> {
                            var selectedRoles = new HashSet<Role>();
                            roles.forEach(
                                    (r, c) -> {
                                        if (c.isSelected()) selectedRoles.add(r);
                                    });
                            String l = login.getText(), n = name.getText();
                            boolean enabled = active.isSelected();
                            String temp = u == null ? temporaryPassword() : null;
                            FxTasks.run(
                                    view,
                                    () ->
                                            u == null
                                                    ? api.post(
                                                            "/api/admin/users",
                                                            new AdminUserCreate(
                                                                    l, n, selectedRoles, temp),
                                                            AdminUserDto.class)
                                                    : api.put(
                                                            "/api/admin/users/" + u.id(),
                                                            new AdminUserUpdate(
                                                                    u.version(),
                                                                    l,
                                                                    n,
                                                                    selectedRoles,
                                                                    enabled),
                                                            AdminUserDto.class),
                                    result -> {
                                        if (temp != null) showPassword(result.login(), temp);
                                        load();
                                    });
                        });
    }

    private void reset() {
        var u = selected();
        if (u == null
                || !AlertHelper.confirm(
                        "Установить временный пароль для «"
                                + u.login()
                                + "» и завершить все его сессии?")) return;
        String temp = temporaryPassword();
        FxTasks.run(
                view,
                () ->
                        api.post(
                                "/api/admin/users/" + u.id() + "/reset-password",
                                new PasswordReset(u.version(), temp),
                                AdminUserDto.class),
                result -> {
                    showPassword(result.login(), temp);
                    load();
                });
    }

    // Generated locally; the server only receives it for hashing and never returns it.
    private String temporaryPassword() {
        byte[] bytes = new byte[18];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "aA1!";
    }

    private void showPassword(String login, String password) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(view.getScene().getWindow());
        a.setTitle("Временный пароль");
        a.setHeaderText(
                "Передайте пароль пользователю «"
                        + login
                        + "». При входе потребуется его сменить.");
        TextField field = new TextField(password);
        field.setEditable(false);
        a.getDialogPane().setContent(field);
        a.showAndWait();
        field.clear();
    }
}
