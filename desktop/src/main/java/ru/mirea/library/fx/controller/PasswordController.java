package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import ru.mirea.library.Main;
import ru.mirea.library.client.ClientContext;
import ru.mirea.library.fx.util.FxTasks;

public class PasswordController {
    @FXML private VBox form;
    @FXML private PasswordField fieldCurrent, fieldNew, fieldConfirm;
    @FXML private Label lblError;
    @FXML private Button btnSave, btnBack;

    @FXML
    private void initialize() {
        btnSave.setOnAction(e -> save());
        btnBack.setOnAction(
                e -> {
                    if (ClientContext.api().user() != null
                            && !ClientContext.api().user().mustChangePassword()) Main.showHome();
                    else logout();
                });
    }

    private void save() {
        String current = fieldCurrent.getText(), replacement = fieldNew.getText();
        if (!replacement.equals(fieldConfirm.getText())) {
            lblError.setText("Новые пароли не совпадают");
            return;
        }
        lblError.setText("");
        fieldCurrent.clear();
        FxTasks.run(
                form,
                () -> {
                    ClientContext.api().changePassword(current, replacement);
                    return null;
                },
                unused -> Main.showLogin("Пароль изменён. Войдите с новым паролем"),
                error -> {
                    if (error instanceof ru.mirea.library.client.ApiException api
                            && api.status() == 401) {
                        lblError.setText("Неверный текущий пароль или сессия истекла");
                    } else lblError.setText(error.getMessage());
                });
    }

    private void logout() {
        FxTasks.run(
                form,
                () -> {
                    ClientContext.api().logout();
                    return null;
                },
                unused -> Main.showLogin(""),
                error -> Main.showLogin(""));
    }
}
