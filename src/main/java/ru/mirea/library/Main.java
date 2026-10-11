package ru.mirea.library;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main-view.fxml")
        );
        Scene scene = new Scene(loader.load(), 1000, 700);
        scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm()
        );
        stage.setTitle("Библиотечная система");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}