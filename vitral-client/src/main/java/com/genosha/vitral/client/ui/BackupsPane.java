package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.ClientSettings;
import com.genosha.vitral.client.api.Models.Backup;
import com.genosha.vitral.client.api.VitralApi;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

/**
 * The copies of the server's catalog database: when they were made, a button to make one now,
 * and a way to download one to keep it somewhere else.
 */
final class BackupsPane extends VBox {

    private final VitralApi api;
    private final ClientSettings settings;
    private final Consumer<Throwable> onError;
    private final ZoneId zone = ZoneId.systemDefault();
    private final TableView<Backup> table = new TableView<>();
    private final Button create = new Button("Fazer backup agora", new FontIcon(Feather.DATABASE));
    private final VBox messages = new VBox();

    BackupsPane(VitralApi api, ClientSettings settings, Consumer<Throwable> onError) {
        this.api = api;
        this.settings = settings;
        this.onError = onError;

        Label about = new Label("O servidor copia o banco do Vitral sozinho, na pasta de dados dele (por padrão, uma vez"
                + " por dia, guardando as últimas 7 cópias). A cópia tem as contas, os álbuns, as favoritas e todo o catálogo, mas não as fotos:"
                + " elas ficam em pastas comuns, e o backup delas é feito como o de qualquer arquivo. Para guardar uma"
                + " cópia fora do servidor, baixe-a aqui.");
        about.setWrapText(true);
        about.getStyleClass().add(Styles.TEXT_MUTED);
        create.getStyleClass().add(Styles.ACCENT);
        create.setOnAction(event -> createNow());
        HBox actions = new HBox(12, create);
        actions.setAlignment(Pos.CENTER_LEFT);

        TableColumn<Backup, String> when = new TableColumn<>("Cópia de");
        when.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Formats.dateTime(cell.getValue().createdAt(), zone)));
        TableColumn<Backup, String> size = new TableColumn<>("Tamanho");
        size.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Formats.fileSize(cell.getValue().sizeBytes())));
        size.setMaxWidth(140);
        TableColumn<Backup, Backup> download = new TableColumn<>();
        download.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        download.setSortable(false);
        download.setMinWidth(110);
        download.setMaxWidth(110);
        download.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button("Baixar", new FontIcon(Feather.DOWNLOAD));

            {
                button.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
            }

            @Override
            protected void updateItem(Backup backup, boolean empty) {
                super.updateItem(backup, empty);
                if (empty || backup == null) {
                    setGraphic(null);
                    return;
                }
                button.setOnAction(event -> downloadCopy(backup));
                setGraphic(button);
            }
        });
        table.getColumns().addAll(List.of(when, size, download));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nenhuma cópia ainda. A primeira é feita alguns minutos depois que o servidor sobe."));
        VBox.setVgrow(table, Priority.ALWAYS);

        setSpacing(16);
        setPadding(new Insets(20));
        getChildren().addAll(about, actions, messages, table);
        reload();
    }

    private void reload() {
        Background.run(api::listBackups, backups -> table.getItems().setAll(backups), error -> {
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    private void createNow() {
        create.setDisable(true);
        Background.run(api::createBackup, backup -> {
            create.setDisable(false);
            show("Cópia feita: " + Formats.fileSize(backup.sizeBytes()) + ".", true);
            reload();
        }, error -> {
            create.setDisable(false);
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    private void downloadCopy(Backup backup) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar cópia do banco");
        chooser.setInitialDirectory(settings.downloadDirectory().toFile());
        chooser.setInitialFileName(backup.name());
        File chosen = chooser.showSaveDialog(getScene().getWindow());
        if (chosen == null) {
            return;
        }
        Path target = chosen.toPath();
        settings.rememberDownloadDirectory(target.getParent());
        show("Baixando " + backup.name() + "…", true);
        Background.run(() -> {
            api.downloadBackup(backup.name(), target);
            return target;
        }, saved -> show("Cópia salva em " + saved, true), error -> {
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    private void show(String text, boolean success) {
        Message message = new Message(null, text, new FontIcon(success ? Feather.CHECK_CIRCLE : Feather.ALERT_CIRCLE));
        message.getStyleClass().add(success ? Styles.SUCCESS : Styles.DANGER);
        messages.getChildren().setAll(message);
    }
}
