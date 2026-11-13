package ru.mirea.library.fx.controller;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import ru.mirea.library.client.*;
import ru.mirea.library.dto.*;
import ru.mirea.library.fx.util.*;

public class NotesWindow {
    private final ApiClient api = ClientContext.api();
    private final VBox root = new VBox(10);
    private final ListView<NoteDto> list = new ListView<>();
    private final TextArea text = new TextArea();
    private final CheckBox pinned = new CheckBox("Закреплена");
    private BookRequestDto request;

    public void show(Window owner, BookRequestDto r) {
        request = r;
        root.setPadding(new Insets(16));
        list.setCellFactory(
                v ->
                        new ListCell<>() {
                            protected void updateItem(NoteDto n, boolean empty) {
                                super.updateItem(n, empty);
                                setText(
                                        empty || n == null
                                                ? null
                                                : (n.pinned() ? "★ " : "")
                                                        + n.authorLogin()
                                                        + " · "
                                                        + n.createdAt()
                                                        + "\n"
                                                        + n.text());
                                setWrapText(true);
                            }
                        });
        list.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, n) -> {
                            text.setText(n == null ? "" : n.text());
                            pinned.setSelected(n != null && n.pinned());
                        });
        var fresh = new Button("Новая");
        fresh.setOnAction(e -> list.getSelectionModel().clearSelection());
        var save = new Button("Сохранить");
        var remove = new Button("Удалить");
        save.setDisable(!api.canEdit() || r.archived());
        remove.setDisable(!api.canEdit());
        save.setOnAction(
                e -> {
                    var n = list.getSelectionModel().getSelectedItem();
                    var body =
                            new NoteWrite(
                                    n == null ? null : n.version(),
                                    text.getText(),
                                    pinned.isSelected());
                    FxTasks.run(
                            root,
                            () ->
                                    n == null
                                            ? api.post(path(), body, NoteDto.class)
                                            : api.put(path() + "/" + n.id(), body, NoteDto.class),
                            v -> {
                                list.getSelectionModel().clearSelection();
                                load();
                            });
                });
        remove.setOnAction(
                e -> {
                    var n = list.getSelectionModel().getSelectedItem();
                    if (n != null && AlertHelper.confirm("Удалить заметку?"))
                        FxTasks.run(
                                root,
                                () -> {
                                    api.delete(path() + "/" + n.id() + "?version=" + n.version());
                                    return null;
                                },
                                v -> load());
                });
        root.getChildren()
                .setAll(
                        new Label("Заметки · заявка №" + r.requestNumber()),
                        list,
                        text,
                        pinned,
                        new HBox(8, fresh, save, remove));
        VBox.setVgrow(list, Priority.ALWAYS);
        text.setPrefRowCount(4);
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("История заметок");
        dialog.setScene(new Scene(root, 650, 550));
        load();
        dialog.show();
    }

    private String path() {
        return "/api/book-requests/" + request.id() + "/notes";
    }

    private void load() {
        FxTasks.run(
                root,
                () -> api.getList(path(), NoteDto.class),
                rows -> list.getItems().setAll(rows));
    }
}
