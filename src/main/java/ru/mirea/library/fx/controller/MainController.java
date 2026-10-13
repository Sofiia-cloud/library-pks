package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import ru.mirea.library.fx.util.AlertHelper;

import java.io.IOException;
import java.io.InputStream;

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
        btnReaders.setOnAction(e -> openView("/fxml/reader-view.fxml", "Читатели"));
        btnRequests.setOnAction(e -> openView("/fxml/request-view.fxml", "Заявки"));
        btnSearch.setOnAction(e -> openView("/fxml/search-view.fxml", "Поиск"));
        btnFilter.setOnAction(e -> openView("/fxml/filter-view.fxml", "Фильтрация"));
        btnSort.setOnAction(e -> openView("/fxml/sort-view.fxml", "Сортировка"));
        btnStatistics.setOnAction(e -> openView("/fxml/statistics-view.fxml", "Статистика"));
        btnExport.setOnAction(e -> showNotImplemented("Экспорт"));
    }

    private void openView(String fxmlPath, String sectionName) {
        // Грузим через getResourceAsStream — обходит проблему с кириллицей в пути
        try (InputStream stream = getClass().getResourceAsStream(fxmlPath)) {
            if (stream == null) {
                AlertHelper.showError("Не найден файл разметки: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader();
            Node view = loader.load(stream);
            contentPane.getChildren().setAll(view);
        } catch (IOException ex) {
            ex.printStackTrace();   // ← временно, для отладки
            AlertHelper.showError("Не удалось открыть раздел «" + sectionName + "»: "
                    + ex.getMessage());
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