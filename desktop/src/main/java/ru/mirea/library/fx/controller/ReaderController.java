package ru.mirea.library.fx.controller;

import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.ReaderDto;
import ru.mirea.library.fx.util.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReaderController {
    @FXML private Parent view;
    @FXML private TableView<ReaderDto> readerTable;
    @FXML private TableColumn<ReaderDto, Integer> colId;
    @FXML private TableColumn<ReaderDto, String> colName, colEmail, colPhone, colCard;
    @FXML private TableColumn<ReaderDto, LocalDateTime> colRegAt;
    @FXML private Button btnAdd, btnEdit, btnDelete, btnRefresh;
    private final ReaderApiClient readers = new ReaderApiClient(ClientContext.api());

    @FXML
    private void initialize() {
        colId.setCellValueFactory(
                c -> new SimpleIntegerProperty(c.getValue().readerNumber()).asObject());
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fullName()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().email()));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().phone()));
        colCard.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().libraryCardNumber()));
        colRegAt.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().registeredAt()));
        colRegAt.setCellFactory(
                column ->
                        new TableCell<>() {
                            protected void updateItem(LocalDateTime date, boolean empty) {
                                super.updateItem(date, empty);
                                setText(
                                        empty || date == null
                                                ? null
                                                : date.format(
                                                        DateTimeFormatter.ofPattern(
                                                                "dd.MM.yyyy HH:mm")));
                            }
                        });
        btnAdd.setDisable(!ClientContext.api().isAdmin());
        btnEdit.setDisable(!ClientContext.api().isAdmin());
        btnDelete.setDisable(!ClientContext.api().isAdmin());
        btnAdd.setOnAction(e -> openForm(null));
        btnEdit.setOnAction(
                e -> {
                    var reader = readerTable.getSelectionModel().getSelectedItem();
                    if (reader == null) AlertHelper.showError("Выберите читателя");
                    else openForm(reader);
                });
        btnDelete.setOnAction(e -> deleteSelected());
        btnRefresh.setOnAction(e -> refresh());
        refresh();
    }

    private void refresh() {
        FxTasks.run(view, readers::list, rows -> readerTable.getItems().setAll(rows));
    }

    private void openForm(ReaderDto reader) {
        try (InputStream stream = getClass().getResourceAsStream("/fxml/reader-form.fxml")) {
            FXMLLoader loader = new FXMLLoader();
            Parent root = loader.load(stream);
            Stage dialog = new Stage();
            dialog.initOwner(view.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle(reader == null ? "Новый читатель" : "Редактирование читателя");
            dialog.setScene(new Scene(root, 460, 370));
            ReaderFormController controller = loader.getController();
            controller.setReader(reader);
            dialog.showAndWait();
            if (controller.isSaved()) refresh();
        } catch (IOException e) {
            AlertHelper.showError("Не удалось открыть форму: " + e.getMessage());
        }
    }

    private void deleteSelected() {
        var reader = readerTable.getSelectionModel().getSelectedItem();
        if (reader == null) {
            AlertHelper.showError("Выберите читателя");
            return;
        }
        if (!AlertHelper.confirm("Удалить читателя «" + reader.fullName() + "»?")) return;
        FxTasks.run(
                view,
                () -> {
                    readers.delete(reader);
                    return null;
                },
                unused -> refresh());
    }
}
