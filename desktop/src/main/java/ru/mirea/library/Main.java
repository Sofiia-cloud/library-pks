package ru.mirea.library;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader();

        // FXML через поток — обходит проблему с кириллицей в пути
        try (InputStream fxmlStream = getClass().getResourceAsStream("/fxml/main-view.fxml")) {
            if (fxmlStream == null) {
                throw new IllegalStateException("Не найден /fxml/main-view.fxml в classpath");
            }
            Parent root = loader.load(fxmlStream);
            Scene scene = new Scene(root, 1000, 700);

            // CSS — тоже через поток
            URL cssUrl = getClass().getResource("/css/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            stage.setTitle("Библиотечная система");
            stage.setScene(scene);
            stage.show();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}