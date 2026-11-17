package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.api.Models.Album;
import com.genosha.vitral.client.api.VitralApi;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/** The user's albums as cards with their covers, and a button to create one. */
final class AlbumsPane extends VBox {

    private static final double COVER = 200;

    private final VitralApi api;
    private final ImageLoader thumbnails;
    private final Consumer<Album> onOpen;
    private final Consumer<Throwable> onError;
    private final FlowPane cards = new FlowPane(16, 20);
    private final StackPane body = new StackPane();

    AlbumsPane(VitralApi api, ImageLoader thumbnails, Consumer<Album> onOpen, Consumer<Throwable> onError) {
        this.api = api;
        this.thumbnails = thumbnails;
        this.onOpen = onOpen;
        this.onError = onError;

        Label title = new Label("Álbuns");
        title.getStyleClass().add(Styles.TITLE_3);
        Button create = new Button("Novo álbum", new FontIcon(Feather.PLUS));
        create.getStyleClass().add(Styles.ACCENT);
        create.setOnAction(event -> createAlbum());
        HBox bar = new HBox(12, title, new Spacer(), create);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 16, 8, 16));

        cards.setPadding(new Insets(8, 16, 24, 16));
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("albums-scroll");
        body.getChildren().setAll(scroll);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(bar, body);
        getStyleClass().add("albums-pane");
        reload();
    }

    void reload() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(40, 40);
        cards.getChildren().setAll(spinner);
        Background.run(() -> api.listAlbums(null), this::show, error -> {
            cards.getChildren().setAll(new Label(ErrorMessages.describe(error)));
            onError.accept(error);
        });
    }

    private void show(List<Album> albums) {
        if (albums.isEmpty()) {
            FontIcon icon = new FontIcon(Feather.BOOK_OPEN);
            icon.getStyleClass().add("state-icon");
            Label title = new Label("Nenhum álbum ainda");
            title.getStyleClass().add(Styles.TITLE_3);
            Label hint = new Label("Crie um álbum aqui, ou abra uma foto e use “Adicionar a um álbum” (tecla A).");
            hint.getStyleClass().add(Styles.TEXT_MUTED);
            hint.setWrapText(true);
            VBox empty = new VBox(10, icon, title, hint);
            empty.setAlignment(Pos.CENTER);
            empty.setMaxWidth(420);
            empty.setPadding(new Insets(80, 0, 0, 0));
            cards.getChildren().setAll(empty);
            return;
        }
        cards.getChildren().setAll(albums.stream().map(this::card).toList());
    }

    private Node card(Album album) {
        StackPane cover = new StackPane();
        cover.setMinSize(COVER, COVER);
        cover.setPrefSize(COVER, COVER);
        cover.setMaxSize(COVER, COVER);
        cover.getStyleClass().add("album-cover");
        Rectangle clip = new Rectangle(COVER, COVER);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        cover.setClip(clip);
        FontIcon placeholder = new FontIcon(Feather.IMAGE);
        placeholder.getStyleClass().add("tile-placeholder");
        cover.getChildren().add(placeholder);
        if (album.coverAssetId() != null) {
            ImageView image = new ImageView();
            image.setFitWidth(COVER);
            image.setFitHeight(COVER);
            image.setSmooth(true);
            cover.getChildren().add(image);
            thumbnails.load(album.coverAssetId()).whenComplete((loaded, error) -> {
                if (loaded != null) {
                    Platform.runLater(() -> {
                        double side = Math.min(loaded.getWidth(), loaded.getHeight());
                        image.setViewport(new Rectangle2D((loaded.getWidth() - side) / 2,
                                (loaded.getHeight() - side) / 2, side, side));
                        image.setImage(loaded);
                        placeholder.setVisible(false);
                    });
                }
            });
        }
        Label name = new Label(album.name());
        name.getStyleClass().add(Styles.TEXT_BOLD);
        name.setMaxWidth(COVER);
        Label count = new Label(Formats.count((int) album.photos(), "foto", "fotos"));
        count.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        VBox card = new VBox(6, cover, name, count);
        card.getStyleClass().add("album-card");
        card.setOnMouseClicked(event -> onOpen.accept(album));
        return card;
    }

    private void createAlbum() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Novo álbum");
        dialog.setHeaderText("Como o álbum vai se chamar?");
        dialog.setContentText("Nome:");
        dialog.showAndWait().map(String::strip).filter(name -> !name.isEmpty()).ifPresent(name ->
                Background.run(() -> api.createAlbum(name), onOpen, error -> {
                    onError.accept(error);
                    Alert failure = new Alert(Alert.AlertType.ERROR, ErrorMessages.describe(error));
                    failure.initOwner(getScene().getWindow());
                    failure.setHeaderText("Não foi possível criar o álbum");
                    failure.showAndWait();
                }));
    }
}
