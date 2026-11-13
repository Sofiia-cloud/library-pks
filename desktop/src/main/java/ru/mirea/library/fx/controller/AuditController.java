package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.*;

import java.util.*;

public class AuditController {
    @FXML private VBox view;
    private final TableView<AuditDto> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label info = new Label();
    private final Button previous = new Button("Назад"), next = new Button("Далее");
    private final ComboBox<String> sort = new ComboBox<>();
    private final CheckBox ascending = new CheckBox("По возрастанию");
    private int page;

    @FXML
    private void initialize() {
        column("Время", a -> a.occurredAt().toString(), 190);
        column("Пользователь", AuditDto::actorLogin, 140);
        column("Событие", AuditDto::action, 150);
        column("Сущность", AuditDto::entityType, 120);
        column("UUID", a -> a.entityId() == null ? "" : a.entityId().toString(), 260);
        column("Описание", AuditDto::description, 300);
        column("Correlation ID", AuditDto::correlationId, 260);
        search.setPromptText("Пользователь, событие, описание, Correlation ID");
        search.setPrefWidth(350);
        sort.getItems().setAll("occurredAt", "actorLogin", "action", "entityType");
        sort.setValue("occurredAt");
        sort.setConverter(
                new javafx.util.StringConverter<String>() {
                    public String toString(String v) {
                        return switch (v) {
                            case "actorLogin" -> "Пользователь";
                            case "action" -> "Событие";
                            case "entityType" -> "Сущность";
                            default -> "Время";
                        };
                    }

                    public String fromString(String v) {
                        return null;
                    }
                });
        var find = new Button("Показать");
        find.setOnAction(
                e -> {
                    page = 0;
                    load();
                });
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
                        new Label("Журнал аудита"),
                        new FlowPane(8, 8, search, sort, ascending, find),
                        table,
                        new HBox(10, previous, info, next));
        VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    private void column(
            String title, java.util.function.Function<AuditDto, String> value, int width) {
        var c = new TableColumn<AuditDto, String>(title);
        c.setCellValueFactory(
                v -> new javafx.beans.property.SimpleStringProperty(value.apply(v.getValue())));
        c.setPrefWidth(width);
        c.setSortable(false);
        table.getColumns().add(c);
    }

    private void load() {
        var f =
                Map.of(
                        "q",
                        search.getText(),
                        "page",
                        page,
                        "size",
                        25,
                        "sort",
                        sort.getValue(),
                        "ascending",
                        ascending.isSelected());
        FxTasks.run(
                view,
                () ->
                        ClientContext.api()
                                .get(
                                        "/api/admin/audit" + ApiClient.query(f),
                                        new com.fasterxml.jackson.core.type.TypeReference<
                                                PageDto<AuditDto>>() {}),
                p -> {
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
}
