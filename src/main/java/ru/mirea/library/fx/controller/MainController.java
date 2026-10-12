package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import ru.mirea.library.fx.util.AlertHelper;

import java.io.IOException;

public class MainController {

    @FXML private StackPane contentPane;

    @FXML private Button btnReaders;
    @FXML private Button btnRequests;
    @FXML private Button btnSearch;
    @FXML private Button btnFilter;
    @FXML private Button btnSort;
    @FXML private Button btnStatistics;
    @FXML private Button btnExport;

    @FXML
    private void initialize() {
        btnReaders.setOnAction(e -> openReaderView());
        btnRequests.setOnAction(e -> openRequestView());
        btnSearch.setOnAction(e -> showNotImplemented("Поиск"));
        btnFilter.setOnAction(e -> showNotImplemented("Фильтрация"));
        btnSort.setOnAction(e -> showNotImplemented("Сортировка"));
        btnStatistics.setOnAction(e -> showNotImplemented("Статистика"));
        btnExport.setOnAction(e -> showNotImplemented("Экспорт"));
    }

    private void openReaderView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/reader-view.fxml")
            );
            Node view = loader.load();
            contentPane.getChildren().setAll(view);
        } catch (IOException ex) {
            AlertHelper.showError("Не удалось открыть раздел «Читатели»: " + ex.getMessage());
        }
    }

    private void openRequestView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/request-view.fxml")
            );
            Node view = loader.load();
            contentPane.getChildren().setAll(view);
        } catch (IOException ex) {
            AlertHelper.showError("Не удалось открыть раздел «Заявки»: " + ex.getMessage());
        }
    }

    private void showNotImplemented(String section) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Раздел в разработке");
        alert.setHeaderText(null);
        alert.setContentText("Раздел «" + section + "» будет реализован позже.");
        alert.showAndWait();
    }
}