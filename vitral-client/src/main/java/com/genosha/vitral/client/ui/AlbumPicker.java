package com.genosha.vitral.client.ui;

import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.api.Models.Album;
import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.VitralApi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puts one photo in albums or takes it out: a list of the user's albums with a mark on those
 * that have it, plus a field to create a new one. Each mark is sent to the server as soon as
 * it changes, so there is nothing to confirm.
 */
final class AlbumPicker {

    private final VitralApi api;
    private final Asset asset;
    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox list = new VBox(8);
    private final Label message = new Label();
    /** The albums that have the photo, as last confirmed by the server. */
    private final Set<UUID> containing = new HashSet<>();

    private AlbumPicker(VitralApi api, Asset asset, Window owner) {
        this.api = api;
        this.asset = asset;
        dialog.initOwner(owner);
        dialog.setTitle("Álbuns");
        dialog.setHeaderText("Em quais álbuns fica " + asset.originalFileName() + "?");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField name = new TextField();
        name.setPromptText("Nome de um álbum novo");
        HBox.setHgrow(name, Priority.ALWAYS);
        Button create = new Button("Criar e adicionar", new FontIcon(Feather.PLUS));
        create.setOnAction(event -> createWith(name));
        name.setOnAction(event -> createWith(name));
        HBox newAlbum = new HBox(8, name, create);
        newAlbum.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(240);
        message.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.DANGER);
        message.setWrapText(true);
        VBox content = new VBox(12, scroll, newAlbum, message);
        content.setPadding(new Insets(12, 16, 4, 16));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
    }

    /**
     * Shows the picker and waits until it is closed.
     *
     * @return the albums that have the photo when the picker closed
     */
    static Set<UUID> show(VitralApi api, Asset asset, Window owner) {
        AlbumPicker picker = new AlbumPicker(api, asset, owner);
        picker.load();
        picker.dialog.showAndWait();
        return Set.copyOf(picker.containing);
    }

    private void load() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(32, 32);
        list.getChildren().setAll(spinner);
        Background.run(() -> api.listAlbums(asset.id()), this::fill,
                error -> list.getChildren().setAll(new Label(ErrorMessages.describe(error))));
    }

    private void fill(List<Album> albums) {
        containing.clear();
        list.getChildren().clear();
        if (albums.isEmpty()) {
            Label none = new Label("Você ainda não tem álbuns. Crie o primeiro abaixo.");
            none.getStyleClass().add(Styles.TEXT_MUTED);
            list.getChildren().add(none);
        }
        for (Album album : albums) {
            if (Boolean.TRUE.equals(album.contains())) {
                containing.add(album.id());
            }
            list.getChildren().add(checkBox(album));
        }
    }

    private CheckBox checkBox(Album album) {
        CheckBox box = new CheckBox(album.name() + "  ·  " + Formats.count((int) album.photos(), "foto", "fotos"));
        box.setSelected(containing.contains(album.id()));
        box.setOnAction(event -> toggle(album, box));
        return box;
    }

    private void toggle(Album album, CheckBox box) {
        boolean add = box.isSelected();
        box.setDisable(true);
        message.setText(null);
        Background.run(() -> add ? api.addToAlbum(album.id(), List.of(asset.id()))
                : api.removeFromAlbum(album.id(), List.of(asset.id())), changed -> {
            box.setDisable(false);
            if (add) {
                containing.add(album.id());
            } else {
                containing.remove(album.id());
            }
        }, error -> {
            box.setDisable(false);
            box.setSelected(!add);
            message.setText(ErrorMessages.describe(error));
        });
    }

    private void createWith(TextField field) {
        String name = field.getText().strip();
        if (name.isEmpty()) {
            return;
        }
        field.setDisable(true);
        message.setText(null);
        Background.run(() -> {
            Album album = api.createAlbum(name);
            api.addToAlbum(album.id(), List.of(asset.id()));
            return api.listAlbums(asset.id());
        }, albums -> {
            field.setDisable(false);
            field.clear();
            fill(albums);
        }, error -> {
            field.setDisable(false);
            message.setText(ErrorMessages.describe(error));
        });
    }
}
