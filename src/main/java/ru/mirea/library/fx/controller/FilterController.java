package ru.mirea.library.fx.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.Reader;
import ru.mirea.library.model.RequestStatus;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.FilterService;
import ru.mirea.library.service.ReaderService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter D_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private ComboBox<RequestStatus> cmbStatus;
    @FXML private Button btnApplyStatus;

    @FXML private ComboBox<Reader> cmbReader;
    @FXML private Button btnApplyReader;

    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Button btnApplyDateRange;

    @FXML private Button btnReset;
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
    private final FilterService filterService =
            new FilterService(new BookRequestRepository());
    private final BookRequestRepository requestRepository =
            new BookRequestRepository();

    private final ObservableList<BookRequest> results =
            FXCollections.observableArrayList();
    private final Map<Integer, Reader> readerCache = new HashMap<>();

    @FXML
    private void initialize() {
        setupColumns();
        resultTable.setItems(results);

        setupStatusCombo();
        setupReaderCombo();

        btnApplyStatus.setOnAction(e -> applyStatus());
        btnApplyReader.setOnAction(e -> applyReader());
        btnApplyDateRange.setOnAction(e -> applyDateRange());
        btnReset.setOnAction(e -> reset());

        loadAll();
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

    private void setupStatusCombo() {
        cmbStatus.setItems(FXCollections.observableArrayList(RequestStatus.values()));
        cmbStatus.setConverter(new StringConverter<>() {
            @Override public String toString(RequestStatus s) {
                return s == null ? "" : s.getRussianName();
            }
            @Override public RequestStatus fromString(String s) { return null; }
        });
    }

    private void setupReaderCombo() {
        try {
            List<Reader> readers = readerService.getAll();
            readerCache.clear();
            for (Reader r : readers) readerCache.put(r.getId(), r);

            cmbReader.setItems(FXCollections.observableArrayList(readers));
            cmbReader.setConverter(new StringConverter<>() {
                @Override public String toString(Reader r) {
                    return r == null ? "" :
                            r.getFullName() + " (" + r.getLibraryCardNumber() + ")";
                }
                @Override public Reader fromString(String s) { return null; }
            });
        } catch (Exception ex) {
            AlertHelper.showError("Не удалось загрузить читателей: " + ex.getMessage());
        }
    }

    private void loadAll() {
        try {
            results.setAll(requestRepository.findAll());
            lblResultCount.setText("Найдено: " + results.size());
        } catch (Exception ex) {
            AlertHelper.showError("Не удалось загрузить заявки: " + ex.getMessage());
        }
    }

    private void applyStatus() {
        RequestStatus status = cmbStatus.getValue();
        if (status == null) {
            AlertHelper.showError("Выберите статус");
            return;
        }
        try {
            List<BookRequest> found = filterService.byStatus(status);
            results.setAll(found);
            lblResultCount.setText("Найдено: " + found.size());
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка фильтрации: " + ex.getMessage());
        }
    }

    private void applyReader() {
        Reader reader = cmbReader.getValue();
        if (reader == null) {
            AlertHelper.showError("Выберите читателя");
            return;
        }
        try {
            List<BookRequest> found = filterService.byReader(reader.getId());
            results.setAll(found);
            lblResultCount.setText("Найдено: " + found.size());
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка фильтрации: " + ex.getMessage());
        }
    }

    private void applyDateRange() {
        LocalDate from = dateFrom.getValue();
        LocalDate to   = dateTo.getValue();
        if (from == null || to == null) {
            AlertHelper.showError("Укажите обе даты");
            return;
        }
        try {
            LocalDateTime fromDt = from.atStartOfDay();
            LocalDateTime toDt   = to.atTime(23, 59, 59);
            List<BookRequest> found = filterService.byDateRange(fromDt, toDt);
            results.setAll(found);
            lblResultCount.setText("Найдено: " + found.size());
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка фильтрации: " + ex.getMessage());
        }
    }

    private void reset() {
        cmbStatus.getSelectionModel().clearSelection();
        cmbReader.getSelectionModel().clearSelection();
        dateFrom.setValue(null);
        dateTo.setValue(null);
        loadAll();
    }
}