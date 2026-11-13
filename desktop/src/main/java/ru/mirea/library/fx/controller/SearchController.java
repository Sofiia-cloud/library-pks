package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.model.SearchField;

import java.util.*;

public class SearchController extends RequestTableController {
    @FXML private ComboBox<String> cmbSearchType;
    @FXML private TextField fieldQuery;
    @FXML private Button btnSearch, btnClear;

    @FXML
    private void initialize() {
        configureTable();
        cmbSearchType
                .getItems()
                .setAll("По названию книги", "По автору", "По ФИО читателя", "По номеру билета");
        cmbSearchType.getSelectionModel().selectFirst();
        btnSearch.setOnAction(e -> search());
        fieldQuery.setOnAction(e -> search());
        btnClear.setOnAction(
                e -> {
                    fieldQuery.clear();
                    showRecords(List.of());
                });
    }

    private void search() {
        String text = fieldQuery.getText().trim();
        if (text.isBlank()) {
            AlertHelper.showError("Введите текст для поиска");
            return;
        }
        SearchField field =
                SearchField.values()[cmbSearchType.getSelectionModel().getSelectedIndex()];
        load(Map.of("q", text, "field", field));
    }
}
