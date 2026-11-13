package ru.mirea.library.fx;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.TableView;
import javafx.scene.image.*;

import org.junit.jupiter.api.*;

import ru.mirea.library.client.ClientContext;
import ru.mirea.library.fx.controller.RequestFormController;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

class FxmlIT {
    @BeforeAll
    static void start() throws Exception {
        String login = System.getProperty("test.api.login"),
                password = System.getProperty("test.api.password");
        assertNotNull(
                login, "Set test.api.login and test.api.password for a dedicated test server");
        assertNotNull(password);
        ClientContext.api().login(login, password);
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    ready.countDown();
                });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stop() {
        try {
            ClientContext.api().logout();
        } finally {
            Platform.exit();
        }
    }

    @Test
    void loadsAllScreensAndPopulatesTablesFromApi() throws Exception {
        String[] files = {
            "login-view",
            "password-view",
            "main-view",
            "reader-view",
            "request-view",
            "request-form",
            "reader-form",
            "search-view",
            "filter-view",
            "sort-view",
            "statistics-view"
        };
        for (String file : files) {
            Parent root =
                    onFx(
                            () -> {
                                FXMLLoader loader = new FXMLLoader();
                                Parent node;
                                try (var stream =
                                        getClass().getResourceAsStream("/fxml/" + file + ".fxml")) {
                                    assertNotNull(stream);
                                    node = loader.load(stream);
                                }
                                if (loader.getController() instanceof RequestFormController form)
                                    form.setRequest(null);
                                int width =
                                        switch (file) {
                                            case "login-view" -> 480;
                                            case "password-view" -> 520;
                                            case "request-form" -> 500;
                                            case "reader-form" -> 460;
                                            default -> 1100;
                                        };
                                int height =
                                        switch (file) {
                                            case "login-view" -> 390;
                                            case "password-view" -> 450;
                                            case "request-form" -> 560;
                                            case "reader-form" -> 370;
                                            default -> 740;
                                        };
                                Scene scene = new Scene(node, width, height);
                                scene.getStylesheets()
                                        .add(
                                                getClass()
                                                        .getResource("/css/styles.css")
                                                        .toExternalForm());
                                node.applyCss();
                                node.layout();
                                return node;
                            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (onFx(root::isDisable) && System.nanoTime() < deadline) Thread.sleep(50);
            assertFalse(onFx(root::isDisable), "Loading timed out for " + file);
            onFx(
                    () -> {
                        root.applyCss();
                        root.layout();
                        if (file.equals("reader-view")) {
                            TableView<?> table = (TableView<?>) root.lookup("#readerTable");
                            assertNotNull(table);
                            assertFalse(table.getItems().isEmpty());
                        }
                        if (file.equals("request-view")
                                || file.equals("filter-view")
                                || file.equals("sort-view")) {
                            TableView<?> table = (TableView<?>) root.lookup("#resultTable");
                            assertNotNull(table);
                            assertFalse(table.getItems().isEmpty());
                        }
                        String directory = System.getProperty("ui.snapshot.dir");
                        if (directory != null) {
                            WritableImage image = root.snapshot(null, null);
                            int width = (int) image.getWidth(), height = (int) image.getHeight();
                            int[] pixels = new int[width * height];
                            image.getPixelReader()
                                    .getPixels(
                                            0,
                                            0,
                                            width,
                                            height,
                                            PixelFormat.getIntArgbInstance(),
                                            pixels,
                                            0,
                                            width);
                            BufferedImage output =
                                    new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                            output.setRGB(0, 0, width, height, pixels, 0, width);
                            Path target = Path.of(directory);
                            Files.createDirectories(target);
                            ImageIO.write(output, "png", target.resolve(file + ".png").toFile());
                        }
                        return null;
                    });
        }
    }

    @Test
    void returnsToLoginAndClosesOwnedFormsWhenSessionExpires() throws Exception {
        onFx(
                () -> {
                    javafx.stage.Stage primary = new javafx.stage.Stage();
                    new ru.mirea.library.Main().start(primary);
                    javafx.stage.Stage dialog = new javafx.stage.Stage();
                    dialog.initOwner(primary);
                    dialog.setScene(new Scene(new javafx.scene.layout.VBox(), 200, 100));
                    dialog.show();
                    assertTrue(dialog.isShowing());
                    ru.mirea.library.Main.showLogin("Сессия истекла");
                    assertFalse(dialog.isShowing());
                    primary.hide();
                    return null;
                });
        assertNull(ClientContext.api().user());
        ClientContext.api()
                .login(
                        System.getProperty("test.api.login"),
                        System.getProperty("test.api.password"));
    }

    @Test
    void exportsExcelUsingRowsReceivedFromApi(@org.junit.jupiter.api.io.TempDir Path directory)
            throws Exception {
        var rows =
                ClientContext.api()
                        .getList(
                                "/api/reports/export-data",
                                ru.mirea.library.dto.ExportRowDto.class);
        assertFalse(rows.isEmpty());
        Path output = directory.resolve("requests.xlsx");
        ru.mirea.library.util.ExcelExporter.exportBookRequests(output.toString());
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(output.toFile())) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(rows.size(), sheet.getLastRowNum());
            assertEquals("Название книги", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals(rows.get(0).bookTitle(), sheet.getRow(1).getCell(2).getStringCellValue());
        }
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(work.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        return result.get(30, TimeUnit.SECONDS);
    }
}
