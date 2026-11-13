package ru.mirea.library;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

import ru.mirea.library.client.ClientContext;
import ru.mirea.library.fx.controller.LoginController;
import ru.mirea.library.fx.util.AlertHelper;

import java.io.*;

public class Main extends Application {
    private static Stage window;

    @Override
    public void start(Stage stage) {
        window = stage;
        stage.setTitle("Библиотечная система");
        showLogin("");
        stage.show();
    }

    public static void showLogin(String message) {
        ClientContext.api().clearSession();
        closeDialogs();
        FXMLLoader loader = show("/fxml/login-view.fxml", 480, 390);
        if (loader != null) ((LoginController) loader.getController()).message(message);
    }

    public static void showHome() {
        show("/fxml/main-view.fxml", 1100, 740);
    }

    public static void showPasswordChange() {
        closeDialogs();
        show("/fxml/password-view.fxml", 520, 450);
    }

    private static void closeDialogs() {
        for (javafx.stage.Window other : java.util.List.copyOf(javafx.stage.Window.getWindows())) {
            if (other != window) other.hide();
        }
    }

    private static FXMLLoader show(String resource, int width, int height) {
        try (InputStream stream = Main.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Не найдена разметка окна");
            FXMLLoader loader = new FXMLLoader();
            Parent root = loader.load(stream);
            Scene scene = new Scene(root, width, height);
            var css = Main.class.getResource("/css/styles.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            window.setScene(scene);
            window.centerOnScreen();
            return loader;
        } catch (IOException e) {
            AlertHelper.showError("Не удалось открыть окно: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void stop() {
        ClientContext.api().clearSession();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
