package ru.mirea.library.fx.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.ReaderService;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReaderController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML private TableView<Reader> readerTable;
    @FXML private TableColumn<Reader, Integer> colId;
    @FXML private TableColumn<Reader, String>  colName;
    @FXML private TableColumn<Reader, String>  colEmail;
    @FXML private TableColumn<Reader, String>  colPhone;
    @FXML private TableColumn<Reader, String>  colCard;
    @FXML private TableColumn<Reader, LocalDateTime> colRegAt;

    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnRefresh;

    private final ReaderService readerService =
            new ReaderService(new ReaderRepository());

    private final ObservableList<Reader> readers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        colCard.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLibraryCardNumber()));
        colRegAt.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getRegisteredAt()));
        colRegAt.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DT_FMT.format(item));
            }
        });

        readerTable.setItems(readers);

        btnAdd.setOnAction(e -> openForm(null));
        btnEdit.setOnAction(e -> openForm(readerTable.getSelectionModel().getSelectedItem()));
        btnDelete.setOnAction(e -> deleteSelected());
        btnRefresh.setOnAction(e -> refresh());

        refresh();
    }

    private void refresh() {
        try {
            readers.setAll(readerService.getAll());
        } catch (Exception ex) {
            AlertHelper.showError("Не удалось загрузить читателей: " + ex.getMessage());
        }
    }

    private void openForm(Reader reader) {
        // Читаем FXML через InputStream — обходит проблему с кириллицей в пути
        try (InputStream stream = getClass()
                .getResourceAsStream("/fxml/reader-form.fxml")) {

            if (stream == null) {
                AlertHelper.showError("Не найден reader-form.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(reader == null
                    ? "Новый читатель"
                    : "Редактирование читателя");
            dialog.setScene(new Scene(loader.load(stream), 420, 340));

            ReaderFormController controller = loader.getController();
            controller.setReader(reader);

            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException ex) {
            AlertHelper.showError("Не удалось открыть форму: " + ex.getMessage());
        }
    }

    private void deleteSelected() {
        Reader selected = readerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showError("Выберите читателя для удаления");
            return;
        }
        if (!AlertHelper.confirm(
                "Удалить читателя «" + selected.getFullName() + "»?")) {
            return;
        }
        try {
            readerService.delete(selected.getId());
            refresh();
            AlertHelper.showInfo("Читатель удалён");
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка удаления: " + ex.getMessage());
        }
    }
}