package ru.mirea.library.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import ru.mirea.library.Main;
import ru.mirea.library.client.ClientContext;
import ru.mirea.library.fx.util.FxTasks;

public class LoginController {
    @FXML private VBox form;
    @FXML private TextField fieldLogin;
    @FXML private PasswordField fieldPassword;
    @FXML private Label lblError;
    @FXML private Button btnLogin;

    @FXML
    private void initialize() {
        btnLogin.setOnAction(e -> login());
        fieldPassword.setOnAction(e -> login());
    }

    public void message(String message) {
        lblError.setText(message);
    }

    private void login() {
        String login = fieldLogin.getText().trim(), password = fieldPassword.getText();
        if (login.isBlank() || password.isBlank()) {
            lblError.setText("Введите логин и пароль");
            return;
        }
        lblError.setText("Выполняется вход…");
        fieldPassword.clear();
        FxTasks.run(
                form,
                () -> ClientContext.api().login(login, password),
                result -> {
                    if (result.user().mustChangePassword()) Main.showPasswordChange();
                    else Main.showHome();
                },
                error -> lblError.setText(error.getMessage()));
    }
}
