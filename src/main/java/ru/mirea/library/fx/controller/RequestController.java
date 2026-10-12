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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.BookRequestService;
import ru.mirea.library.service.ReaderService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class RequestController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter D_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private TableView<BookRequest> requestTable;
    @FXML private TableColumn<BookRequest, Integer>       colId;
    @FXML private TableColumn<BookRequest, String>        colReader;
    @FXML private TableColumn<BookRequest, String>        colBookTitle;
    @FXML private TableColumn<BookRequest, String>        colAuthor;
    @FXML private TableColumn<BookRequest, String>        colIsbn;
    @FXML private TableColumn<BookRequest, String>        colStatus;
    @FXML private TableColumn<BookRequest, LocalDateTime> colCreatedAt;
    @FXML private TableColumn<BookRequest, LocalDate>     colReturnDate;

    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnRefresh;

    private final ReaderService readerService =
            new ReaderService(new ReaderRepository());
    private final BookRequestService requestService =
            new BookRequestService(new BookRequestRepository(), readerService);

    private final ObservableList<BookRequest> requests =
            FXCollections.observableArrayList();

    private final Map<Integer, Reader> readerCache = new HashMap<>();

    @FXML
    private void initialize() {
        colId.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colBookTitle.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBookTitle()));
        colAuthor.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBookAuthor()));
        colIsbn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getIsbn()));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().getRussianName()));

        colReader.setCellValueFactory(c -> {
            Reader r = readerCache.get(c.getValue().getReaderId());
            return new SimpleStringProperty(r == null ? "?" : r.getFullName());
        });

        colCreatedAt.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getCreatedAt()));
        colCreatedAt.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DT_FMT.format(item));
            }
        });

        colReturnDate.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getDesiredReturnDate()));
        colReturnDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "—" : D_FMT.format(item));
            }
        });

        requestTable.setItems(requests);

        btnAdd.setOnAction(e -> openForm(null));
        btnEdit.setOnAction(e -> openForm(requestTable.getSelectionModel().getSelectedItem()));
        btnDelete.setOnAction(e -> deleteSelected());
        btnRefresh.setOnAction(e -> refresh());

        refresh();
    }

    private void refresh() {
        try {
            readerCache.clear();
            for (Reader r : readerService.getAll()) {
                readerCache.put(r.getId(), r);
            }
            requests.setAll(requestService.getAll());
        } catch (Exception ex) {
            AlertHelper.showError("Не удалось загрузить заявки: " + ex.getMessage());
        }
    }

    private void openForm(BookRequest request) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/request-form.fxml"));
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(request == null
                    ? "Новая заявка"
                    : "Редактирование заявки №" + request.getId());
            dialog.setScene(new Scene(loader.load(), 480, 520));

            RequestFormController controller = loader.getController();
            controller.setReaders(readerService.getAll());   
            controller.setRequest(request);                 

            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException ex) {
            AlertHelper.showError("Не удалось открыть форму: " + ex.getMessage());
        }
    }

    private void deleteSelected() {
        BookRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showError("Выберите заявку для удаления");
            return;
        }
        if (!AlertHelper.confirm(
                "Удалить заявку №" + selected.getId()
                        + " («" + selected.getBookTitle() + "»)?")) {
            return;
        }
        try {
            requestService.delete(selected.getId());
            refresh();
            AlertHelper.showInfo("Заявка удалена");
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка удаления: " + ex.getMessage());
        }
    }
}