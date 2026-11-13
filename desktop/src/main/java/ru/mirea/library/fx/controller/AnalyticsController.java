package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import ru.mirea.library.client.ClientContext;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.*;

public class AnalyticsController {
    @FXML private VBox view;
    private final Label totals = new Label();
    private final TableView<NameCountDto> table = new TableView<>();

    @FXML
    private void initialize() {
        totals.setWrapText(true);
        var refresh = new Button("Обновить");
        refresh.setOnAction(e -> load());
        var name = new TableColumn<NameCountDto, String>("Группа");
        name.setCellValueFactory(
                v -> new javafx.beans.property.SimpleStringProperty(v.getValue().name()));
        name.setPrefWidth(440);
        var count = new TableColumn<NameCountDto, Long>("Количество");
        count.setCellValueFactory(
                v -> new javafx.beans.property.SimpleLongProperty(v.getValue().count()).asObject());
        table.getColumns().add(name);
        table.getColumns().add(count);
        view.getChildren().setAll(new Label("Аналитическая сводка"), totals, refresh, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    private void load() {
        FxTasks.run(
                view,
                () -> ClientContext.api().get("/api/analytics/summary", AnalyticsDto.class),
                a -> {
                    totals.setText(
                            "Всего заявок: "
                                    + a.total()
                                    + " · Активных (вне архива): "
                                    + a.active()
                                    + " · Архивных: "
                                    + a.archived()
                                    + "\nСрочных: "
                                    + a.urgent()
                                    + " · Среднее количество экземпляров: "
                                    + String.format("%.2f", a.averageCopies()));
                    var rows = new java.util.ArrayList<NameCountDto>();
                    a.byReader()
                            .forEach(
                                    r ->
                                            rows.add(
                                                    new NameCountDto(
                                                            "Читатель · " + r.name(), r.count())));
                    a.byStatus()
                            .forEach(
                                    (s, n) ->
                                            rows.add(
                                                    new NameCountDto(
                                                            "Статус · " + Labels.text(s), n)));
                    a.byReturnState()
                            .forEach(
                                    (s, n) ->
                                            rows.add(
                                                    new NameCountDto(
                                                            "Состояние · " + Labels.text(s), n)));
                    table.getItems().setAll(rows);
                });
    }
}
