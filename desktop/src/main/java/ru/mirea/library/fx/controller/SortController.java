package ru.mirea.library.fx.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.service.SortService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SortController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter D_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final String FIELD_DATE   = "По дате создания";
    private static final String FIELD_TITLE  = "По названию книги";
    private static final String FIELD_STATUS = "По статусу";

    private static final String DIR_ASC  = "По возрастанию";
    private static final String DIR_DESC = "По убыванию";

    @FXML private ComboBox<String> cmbField;
    @FXML private ComboBox<String> cmbDirection;
    @FXML private Button btnApply;
    @FXML private Label lblResultCount;

    @FXML private TableView<BookRequest> resultTable;
    @FXML private TableColumn<BookRequest, Integer>       colId;
    @FXML private TableColumn<BookRequest, String>        colReader;
    @FXML private TableColumn<BookRequest, String>        colBookTitle;
    @FXML private TableColumn<BookRequest, String>        colAuthor;
    @FXML private TableColumn<BookRequest, String>        colIsbn;
    @FXML private TableColumn<BookRequest, String>        colStatus;
    @FXML private TableColumn<BookRequest, LocalDateTime> colCreatedAt;
    @FXML private TableColumn<BookRequest, LocalDate>     colReturnDate;

    private final ReaderService readerService =
            new ReaderService(new ReaderRepository());
    private final SortService sortService =
            new SortService(new BookRequestRepository());

    private final ObservableList<BookRequest> results =
            FXCollections.observableArrayList();
    private final Map<Integer, Reader> readerCache = new HashMap<>();

    @FXML
    private void initialize() {
        cmbField.setItems(FXCollections.observableArrayList(
                FIELD_DATE, FIELD_TITLE, FIELD_STATUS));
        cmbField.getSelectionModel().selectFirst();

        cmbDirection.setItems(FXCollections.observableArrayList(
                DIR_ASC, DIR_DESC));
        cmbDirection.getSelectionModel().selectFirst();

        setupColumns();
        resultTable.setItems(results);

        btnApply.setOnAction(e -> apply());

        loadReaderCache();
        apply();
    }

    private void setupColumns() {
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
            @Override protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DT_FMT.format(item));
            }
        });
        colReturnDate.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getDesiredReturnDate()));
        colReturnDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "—" : D_FMT.format(item));
            }
        });
    }

    private void loadReaderCache() {
        try {
            readerCache.clear();
            for (Reader r : readerService.getAll()) {
                readerCache.put(r.getId(), r);
            }
        } catch (Exception ex) {
            AlertHelper.showError("Не удалось загрузить читателей: " + ex.getMessage());
        }
    }

    private void apply() {
        String field = cmbField.getValue();
        String dir = cmbDirection.getValue();
        boolean asc = DIR_ASC.equals(dir);

        try {
            List<BookRequest> sorted = switch (field) {
                case FIELD_DATE   -> sortService.byCreatedAt(asc);
                case FIELD_TITLE  -> sortService.byBookTitle(asc);
                case FIELD_STATUS -> sortService.byStatus(asc);
                default -> List.of();
            };
            results.setAll(sorted);
            lblResultCount.setText("Показано: " + sorted.size());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка сортировки: " + ex.getMessage());
        }
    }
}