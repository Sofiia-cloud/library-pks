package ru.mirea.library.fx.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.mirea.library.exception.BusinessException;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.BookRequest;
import ru.mirea.library.model.Reader;
import ru.mirea.library.repository.BookRequestRepository;
import ru.mirea.library.repository.ReaderRepository;
import ru.mirea.library.service.ReaderService;
import ru.mirea.library.service.SearchService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchController {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter D_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private ComboBox<String> cmbSearchType;
    @FXML private TextField fieldQuery;
    @FXML private Button btnSearch;
    @FXML private Button btnClear;
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
    private final SearchService searchService =
            new SearchService(new BookRequestRepository());

    private final ObservableList<BookRequest> results =
            FXCollections.observableArrayList();
    private final Map<Integer, Reader> readerCache = new HashMap<>();

    private static final String TYPE_TITLE  = "По названию книги";
    private static final String TYPE_AUTHOR = "По автору";
    private static final String TYPE_READER = "По ФИО читателя";
    private static final String TYPE_CARD   = "По номеру билета";

    @FXML
    private void initialize() {
        cmbSearchType.setItems(FXCollections.observableArrayList(
                TYPE_TITLE, TYPE_AUTHOR, TYPE_READER, TYPE_CARD));
        cmbSearchType.getSelectionModel().selectFirst();

        setupColumns();
        resultTable.setItems(results);

        btnSearch.setOnAction(e -> doSearch());
        btnClear.setOnAction(e -> clear());
        fieldQuery.setOnAction(e -> doSearch());

        loadReaderCache();
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

    private void doSearch() {
        String query = fieldQuery.getText();
        if (query == null || query.isBlank()) {
            AlertHelper.showError("Введите текст для поиска");
            return;
        }
        String type = cmbSearchType.getValue();
        try {
            List<BookRequest> found = switch (type) {
                case TYPE_TITLE  -> searchService.byTitle(query.trim());
                case TYPE_AUTHOR -> searchService.byAuthor(query.trim());
                case TYPE_READER -> searchService.byReaderName(query.trim());
                case TYPE_CARD   -> searchService.byReaderCard(query.trim());
                default -> List.of();
            };
            results.setAll(found);
            lblResultCount.setText("Найдено: " + found.size());
        } catch (BusinessException ex) {
            AlertHelper.showError(ex.getMessage());
        } catch (Exception ex) {
            AlertHelper.showError("Ошибка поиска: " + ex.getMessage());
        }
    }

    private void clear() {
        fieldQuery.clear();
        results.clear();
        lblResultCount.setText("Найдено: 0");
        cmbSearchType.getSelectionModel().selectFirst();
    }
}