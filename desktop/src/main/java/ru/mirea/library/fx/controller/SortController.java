package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import ru.mirea.library.model.RequestSort;

import java.util.Map;

public class SortController extends RequestTableController {
    @FXML private ComboBox<String> cmbField, cmbDirection;
    @FXML private Button btnApply;

    @FXML
    private void initialize() {
        configureTable();
        cmbField.getItems().setAll("По дате создания", "По названию книги", "По статусу");
        cmbField.getSelectionModel().selectFirst();
        cmbDirection.getItems().setAll("По возрастанию", "По убыванию");
        cmbDirection.getSelectionModel().selectFirst();
        btnApply.setOnAction(e -> apply());
        apply();
    }

    private void apply() {
        RequestSort sort =
                switch (cmbField.getSelectionModel().getSelectedIndex()) {
                    case 1 -> RequestSort.TITLE;
                    case 2 -> RequestSort.STATUS;
                    default -> RequestSort.CREATED_AT;
                };
        load(
                Map.of(
                        "sort",
                        sort,
                        "ascending",
                        cmbDirection.getSelectionModel().getSelectedIndex() == 0));
    }
}
