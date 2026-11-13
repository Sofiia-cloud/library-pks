package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import ru.mirea.library.Main;
import ru.mirea.library.client.ClientContext;
import ru.mirea.library.fx.util.AlertHelper;
import ru.mirea.library.fx.util.FxTasks;
import ru.mirea.library.util.ExcelExporter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class MainController {

    @FXML private StackPane contentPane;
    @FXML private Label lblUser;
    @FXML private Button btnPassword, btnLogout;

    @FXML private Button btnReaders;
    @FXML private Button btnRequests;
    @FXML private Button btnSearch;
    @FXML private Button btnFilter;
    @FXML private Button btnSort;
    @FXML private Button btnStatistics;
    @FXML private Button btnExport;

    @FXML
    private void initialize() {
        var user = ClientContext.api().user();
        lblUser.setText(user.name() + "\n" + user.roles());
        btnPassword.setOnAction(e -> Main.showPasswordChange());
        btnLogout.setOnAction(
                e ->
                        FxTasks.run(
                                btnLogout,
                                () -> {
                                    ClientContext.api().logout();
                                    return null;
                                },
                                unused -> Main.showLogin(""),
                                error -> Main.showLogin("")));
        btnReaders.setOnAction(e -> openView("/fxml/reader-view.fxml", "Читатели"));
        btnRequests.setOnAction(e -> openView("/fxml/request-view.fxml", "Заявки"));
        btnSearch.setOnAction(e -> openView("/fxml/search-view.fxml", "Поиск"));
        btnFilter.setOnAction(e -> openView("/fxml/filter-view.fxml", "Фильтрация"));
        btnSort.setOnAction(e -> openView("/fxml/sort-view.fxml", "Сортировка"));
        btnStatistics.setOnAction(e -> openView("/fxml/statistics-view.fxml", "Статистика"));
        btnExport.setOnAction(e -> exportRequests());
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
            AlertHelper.showError(
                    "Не удалось открыть раздел «" + sectionName + "»: " + ex.getMessage());
        }
    }

    private void exportRequests() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт заявок в Excel");
        chooser.setInitialFileName("library_requests.xlsx");
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Книга Excel (*.xlsx)", "*.xlsx"));

        Window owner = contentPane.getScene() == null ? null : contentPane.getScene().getWindow();
        File selectedFile = chooser.showSaveDialog(owner);
        if (selectedFile == null) {
            return;
        }

        File outputFile =
                selectedFile.getName().toLowerCase().endsWith(".xlsx")
                        ? selectedFile
                        : new File(selectedFile.getParentFile(), selectedFile.getName() + ".xlsx");
        FxTasks.run(
                btnExport,
                () -> {
                    ExcelExporter.exportBookRequests(outputFile.getAbsolutePath());
                    return null;
                },
                unused ->
                        AlertHelper.showInfo(
                                "Заявки экспортированы в файл:\n" + outputFile.getAbsolutePath()));
    }
}
