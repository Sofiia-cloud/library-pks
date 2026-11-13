package ru.mirea.library.fx.controller;

import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.BookRequestDto;
import ru.mirea.library.fx.util.FxTasks;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class RequestTableController {
    @FXML protected Parent view;
    @FXML protected TableView<BookRequestDto> resultTable;
    @FXML protected TableColumn<BookRequestDto, Integer> colId;
    @FXML
    protected TableColumn<BookRequestDto, String> colReader,
            colBookTitle,
            colAuthor,
            colIsbn,
            colStatus;
    @FXML protected TableColumn<BookRequestDto, LocalDateTime> colCreatedAt;
    @FXML protected TableColumn<BookRequestDto, LocalDate> colReturnDate;
    @FXML protected Label lblResultCount;
    protected final BookRequestApiClient requests = new BookRequestApiClient(ClientContext.api());

    protected final Map<String, Object> currentFilters = new LinkedHashMap<>();
    private int page;
    private int totalPages;
    private final Label pageLabel = new Label();
    private final Button previous = new Button("Назад"), next = new Button("Далее");
    private final ComboBox<Integer> pageSize = new ComboBox<>();

    protected void reload() {
        loadPage();
    }

    protected void configureTable() {
        colId.setCellValueFactory(
                c -> new SimpleIntegerProperty(c.getValue().requestNumber()).asObject());
        colReader.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().readerName()));
        colBookTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().bookTitle()));
        colAuthor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().bookAuthor()));
        colIsbn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isbn()));
        colStatus.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().status().getRussianName()));
        colCreatedAt.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().createdAt()));
        colReturnDate.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().desiredReturnDate()));
        colCreatedAt.setCellFactory(
                column ->
                        new TableCell<>() {
                            protected void updateItem(LocalDateTime value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : value.format(
                                                        DateTimeFormatter.ofPattern(
                                                                "dd.MM.yyyy HH:mm")));
                            }
                        });
        colReturnDate.setCellFactory(
                column ->
                        new TableCell<>() {
                            protected void updateItem(LocalDate value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : value.format(
                                                        DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                            }
                        });
        pageSize.getItems().setAll(10, 25, 50, 100);
        pageSize.setValue(25);
        previous.setOnAction(
                e -> {
                    page--;
                    loadPage();
                });
        next.setOnAction(
                e -> {
                    page++;
                    loadPage();
                });
        pageSize.setOnAction(
                e -> {
                    page = 0;
                    loadPage();
                });
        var export = new Button("Экспорт CSV");
        export.setOnAction(e -> exportCsv());
        var bar =
                new javafx.scene.layout.HBox(
                        10, previous, pageLabel, next, new Label("На странице:"), pageSize, export);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (view instanceof javafx.scene.layout.VBox box) box.getChildren().add(bar);
        resultTable.setPlaceholder(new Label("Нет записей"));
        for (var column : resultTable.getColumns()) column.setSortable(false);
    }

    protected void load(Map<String, ?> filters) {
        currentFilters.clear();
        currentFilters.putAll(filters);
        page = 0;
        loadPage();
    }

    private void loadPage() {
        var filters = new LinkedHashMap<String, Object>(currentFilters);
        filters.put("page", page);
        filters.put("size", pageSize.getValue());
        FxTasks.run(
                view,
                () -> requests.page(filters),
                result -> {
                    totalPages = result.totalPages();
                    if (page > 0 && page >= totalPages) {
                        page = Math.max(0, totalPages - 1);
                        loadPage();
                        return;
                    }
                    showRecords(result.items());
                    pageLabel.setText(
                            (page + 1)
                                    + " / "
                                    + Math.max(1, totalPages)
                                    + " · Всего: "
                                    + result.totalElements());
                    previous.setDisable(page == 0);
                    next.setDisable(page + 1 >= totalPages);
                    if (lblResultCount != null)
                        lblResultCount.setText("Всего: " + result.totalElements());
                });
    }

    private void exportCsv() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Экспорт с текущими фильтрами");
        chooser.setInitialFileName("book-requests.csv");
        var file = chooser.showSaveDialog(view.getScene().getWindow());
        if (file == null) return;
        var filters = new LinkedHashMap<String, Object>(currentFilters);
        FxTasks.run(
                view,
                () -> {
                    String csv =
                            ClientContext.api()
                                    .getText(
                                            "/api/book-requests/export" + ApiClient.query(filters));
                    java.nio.file.Files.writeString(
                            file.toPath(), csv, java.nio.charset.StandardCharsets.UTF_8);
                    return null;
                },
                v -> ru.mirea.library.fx.util.AlertHelper.showInfo("CSV сохранён"));
    }

    protected void showRecords(List<BookRequestDto> rows) {
        resultTable.getItems().setAll(rows);
        if (lblResultCount != null) lblResultCount.setText("Найдено: " + rows.size());
    }
}
