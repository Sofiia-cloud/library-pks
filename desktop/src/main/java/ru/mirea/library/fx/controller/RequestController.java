package ru.mirea.library.fx.controller;

import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.stage.*;

import ru.mirea.library.client.ClientContext;
import ru.mirea.library.dto.BookRequestDto;
import ru.mirea.library.fx.util.*;

import java.io.*;
import java.util.Map;

public class RequestController extends RequestTableController {
    @FXML private Button btnAdd, btnEdit, btnDelete, btnRefresh;

    @FXML
    private void initialize() {
        configureTable();
        btnAdd.setDisable(!ClientContext.api().canEdit());
        btnEdit.setDisable(!ClientContext.api().canEdit());
        btnDelete.setDisable(!ClientContext.api().isAdmin());
        btnAdd.setOnAction(e -> openForm(null));
        btnEdit.setOnAction(
                e -> {
                    var selected = resultTable.getSelectionModel().getSelectedItem();
                    if (selected == null) AlertHelper.showError("Выберите заявку");
                    else openForm(selected);
                });
        btnDelete.setOnAction(e -> deleteSelected());
        btnRefresh.setOnAction(e -> load(Map.of()));
        load(Map.of());
    }

    private void openForm(BookRequestDto request) {
        try (InputStream stream = getClass().getResourceAsStream("/fxml/request-form.fxml")) {
            FXMLLoader loader = new FXMLLoader();
            Parent root = loader.load(stream);
            Stage dialog = new Stage();
            dialog.initOwner(view.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle(
                    request == null ? "Новая заявка" : "Заявка №" + request.requestNumber());
            dialog.setScene(new Scene(root, 500, 560));
            RequestFormController controller = loader.getController();
            controller.setRequest(request);
            dialog.showAndWait();
            if (controller.isSaved()) load(Map.of());
        } catch (IOException e) {
            AlertHelper.showError("Не удалось открыть форму: " + e.getMessage());
        }
    }

    private void deleteSelected() {
        var selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showError("Выберите заявку");
            return;
        }
        if (!AlertHelper.confirm(
                "Удалить заявку №"
                        + selected.requestNumber()
                        + " («"
                        + selected.bookTitle()
                        + "»)?")) return;
        FxTasks.run(
                view,
                () -> {
                    requests.delete(selected);
                    return null;
                },
                unused -> load(Map.of()));
    }
}
