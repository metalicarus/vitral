package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.api.Models.Folder;
import com.genosha.vitral.client.api.Models.User;
import com.genosha.vitral.client.api.VitralApi;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The watched folders: folders of the server whose photos go into someone's library where
 * they are, such as the folder Syncthing keeps in sync with a phone. While on screen, the
 * list refreshes every few seconds, so a scan's progress shows up as it happens.
 */
final class FoldersPane extends VBox {

    private static final Duration REFRESH = Duration.seconds(3);

    private final VitralApi api;
    private final Consumer<Throwable> onError;
    private final Runnable onLibraryChanged;
    private final ZoneId zone = ZoneId.systemDefault();

    private final TableView<Folder> table = new TableView<>();
    private final TextField path = new TextField();
    private final ComboBox<String> owner = new ComboBox<>();
    private final Button add = new Button("Monitorar pasta", new FontIcon(Feather.FOLDER_PLUS));
    private final VBox messages = new VBox();
    private final Timeline refresh = new Timeline(new KeyFrame(REFRESH, event -> reload()));

    /** @param onLibraryChanged called when a folder is added or removed, since the gallery then changes */
    FoldersPane(VitralApi api, Consumer<Throwable> onError, Runnable onLibraryChanged) {
        this.api = api;
        this.onError = onError;
        this.onLibraryChanged = onLibraryChanged;

        table.getColumns().add(pathColumn());
        table.getColumns().add(column("Fotos de", Folder::ownerEmail));
        TableColumn<Folder, Long> photos = new TableColumn<>("Fotos");
        photos.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().photos()));
        photos.setMaxWidth(90);
        table.getColumns().add(photos);
        table.getColumns().add(statusColumn());
        table.getColumns().add(actionsColumn());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nenhuma pasta monitorada ainda."));
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh.setCycleCount(Timeline.INDEFINITE);

        setSpacing(16);
        setPadding(new Insets(20));
        getChildren().addAll(table, form());
        reload();
        reloadOwners();
    }

    /** Refreshes the list every few seconds while {@code active}. */
    void setActive(boolean active) {
        if (active) {
            reload();
            refresh.play();
        } else {
            refresh.stop();
        }
    }

    void dispose() {
        refresh.stop();
    }

    /** Fills the owner choice with the server's accounts, for example after one is created. */
    void reloadOwners() {
        Background.run(api::listUsers, users -> {
            String selected = owner.getValue();
            owner.getItems().setAll(users.stream().map(User::email).toList());
            owner.setValue(selected != null && owner.getItems().contains(selected) ? selected : null);
        }, onError);
    }

    private VBox form() {
        Label heading = new Label("Monitorar uma pasta");
        heading.getStyleClass().add(Styles.TITLE_4);
        Label hint = new Label("O caminho é de uma pasta no servidor, não neste computador. Se o servidor roda no Docker,"
                + " use o caminho de dentro do contêiner. O Vitral só lê a pasta: nunca altera, move nem apaga"
                + " nada nela.");
        hint.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        hint.setWrapText(true);

        path.setPromptText("/fotos/celular-maria");
        HBox.setHgrow(path, Priority.ALWAYS);
        owner.setPromptText("De quem são as fotos");
        owner.setPrefWidth(260);
        add.getStyleClass().add(Styles.ACCENT);
        add.setOnAction(event -> addFolder());
        HBox fields = new HBox(12, path, owner, add);
        fields.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(10, heading, hint, fields, messages);
        form.getStyleClass().add("admin-form");
        form.setPadding(new Insets(16));
        return form;
    }

    private void reload() {
        Background.run(api::listFolders, folders -> {
            Folder selected = table.getSelectionModel().getSelectedItem();
            table.getItems().setAll(folders);
            if (selected != null) {
                folders.stream().filter(folder -> folder.id().equals(selected.id())).findFirst()
                        .ifPresent(folder -> table.getSelectionModel().select(folder));
            }
        }, error -> {
            refresh.stop();
            table.setPlaceholder(new Label(ErrorMessages.describe(error)));
            onError.accept(error);
        });
    }

    private void addFolder() {
        String pathValue = path.getText().strip();
        String ownerValue = owner.getValue();
        if (pathValue.isEmpty() || ownerValue == null) {
            show("Informe o caminho da pasta e de quem são as fotos.", false);
            return;
        }
        add.setDisable(true);
        Background.run(() -> api.addFolder(pathValue, ownerValue), folder -> {
            add.setDisable(false);
            path.clear();
            show("Pasta cadastrada. As fotos aparecem na biblioteca de " + folder.ownerEmail()
                    + " conforme a varredura avança.", true);
            onLibraryChanged.run();
            reload();
        }, error -> {
            add.setDisable(false);
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    private void scan(Folder folder) {
        Background.run(() -> {
            api.scanFolder(folder.id());
            return null;
        }, ignored -> show("Varredura de " + folder.path() + " pedida.", true), error -> {
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    private void remove(Folder folder) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "As fotos que vieram desta pasta saem da biblioteca de " + folder.ownerEmail()
                        + "; as que também foram enviadas pelo app continuam. Os arquivos na pasta não são apagados"
                        + " nem alterados.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(getScene().getWindow());
        confirm.setTitle("Parar de monitorar");
        confirm.setHeaderText("Parar de monitorar " + folder.path() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        Background.run(() -> {
            api.removeFolder(folder.id());
            return null;
        }, ignored -> {
            show("A pasta " + folder.path() + " não é mais monitorada.", true);
            onLibraryChanged.run();
            reload();
        }, error -> {
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    /** The folder's path, in full when hovered: long paths do not fit the column. */
    private TableColumn<Folder, String> pathColumn() {
        TableColumn<Folder, String> column = column("Pasta no servidor", Folder::path);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty ? null : path);
                setTooltip(empty || path == null ? null : new Tooltip(path));
            }
        });
        return column;
    }

    /** When the folder was last scanned, or why it could not be. */
    private TableColumn<Folder, Folder> statusColumn() {
        TableColumn<Folder, Folder> column = new TableColumn<>("Última varredura");
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Folder folder, boolean empty) {
                super.updateItem(folder, empty);
                getStyleClass().remove(Styles.DANGER);
                setTooltip(null);
                if (empty || folder == null) {
                    setText(null);
                } else if (folder.lastScanError() != null) {
                    setText("Não foi possível ler a pasta");
                    setTooltip(new Tooltip(folder.lastScanError()));
                    getStyleClass().add(Styles.DANGER);
                } else if (folder.lastScanAt() == null) {
                    setText("Varrendo pela primeira vez…");
                } else {
                    setText(Formats.dateTime(folder.lastScanAt(), zone));
                }
            }
        });
        return column;
    }

    private TableColumn<Folder, Folder> actionsColumn() {
        TableColumn<Folder, Folder> column = new TableColumn<>();
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setMinWidth(110);
        column.setMaxWidth(110);
        column.setSortable(false);
        column.setCellFactory(col -> new TableCell<>() {
            private final Button scanButton = iconButton(Feather.REFRESH_CW, "Varrer agora");
            private final Button removeButton = iconButton(Feather.TRASH_2, "Parar de monitorar");
            private final HBox buttons = new HBox(4, scanButton, removeButton);

            {
                removeButton.getStyleClass().add(Styles.DANGER);
                buttons.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Folder folder, boolean empty) {
                super.updateItem(folder, empty);
                if (empty || folder == null) {
                    setGraphic(null);
                    return;
                }
                scanButton.setOnAction(event -> scan(folder));
                removeButton.setOnAction(event -> remove(folder));
                setGraphic(buttons);
            }
        });
        return column;
    }

    private static Button iconButton(Feather icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private void show(String text, boolean success) {
        Message message = new Message(null, text, new FontIcon(success ? Feather.CHECK_CIRCLE : Feather.ALERT_CIRCLE));
        message.getStyleClass().add(success ? Styles.SUCCESS : Styles.DANGER);
        messages.getChildren().setAll(message);
    }

    private static TableColumn<Folder, String> column(String title, Function<Folder, String> value) {
        TableColumn<Folder, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }
}
