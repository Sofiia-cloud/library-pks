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
        resultTable.setPlaceholder(new Label("Нет записей"));
        for (var column : resultTable.getColumns()) column.setSortable(false);
    }

    protected void load(Map<String, ?> filters) {
        FxTasks.run(view, () -> requests.list(filters), this::showRecords);
    }

    protected void showRecords(List<BookRequestDto> rows) {
        resultTable.getItems().setAll(rows);
        if (lblResultCount != null) lblResultCount.setText("Найдено: " + rows.size());
    }
}
