package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.ClientSettings;
import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.Models.TakenAtSource;
import com.genosha.vitral.client.api.Models.ThumbnailStatus;
import com.genosha.vitral.client.api.VitralApi;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * One photo at a time, as large as the window allows, over a dark background.
 *
 * <p>It shows the preview (at most 1440 px) rather than the original, which can be tens of
 * megabytes; the original is downloaded only when asked. While the preview loads, the
 * thumbnail already in memory is shown enlarged, so something appears at once.
 *
 * <p>Keys: left and right arrows move between photos, I shows the details, Esc closes.
 */
final class ViewerView extends StackPane {

    /** What the viewer needs from the gallery that opened it. */
    interface Host {
        /** The gallery's photos, newest first; the list grows as more pages load. */
        List<Asset> assets();

        /** Whether the server has more photos than the ones loaded. */
        boolean hasMore();

        void loadMore();

        /** The viewer was closed while showing {@code current}. */
        void closed(Asset current);

        void sessionExpired();
    }

    /** Asks the gallery for the next page when the viewer gets this close to the last loaded photo. */
    private static final int LOAD_AHEAD = 5;
    /** Room on each side of the photo for the previous and next buttons. */
    private static final double SIDE_ROOM = 72;
    private static final double INFO_WIDTH = 300;

    private final Host host;
    private final VitralApi api;
    private final ImageLoader thumbnails;
    private final ImageLoader previews;
    private final ClientSettings settings;
    private final ZoneId zone;

    private final ImageView imageView = new ImageView();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label notice = new Label();
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final Label status = new Label();
    private final Button previous = navigationButton(Feather.CHEVRON_LEFT, "Anterior (←)");
    private final Button next = navigationButton(Feather.CHEVRON_RIGHT, "Próxima (→)");
    private final Button download = new Button("Baixar original", new FontIcon(Feather.DOWNLOAD));
    private final ToggleButton infoToggle = new ToggleButton(null, new FontIcon(Feather.INFO));
    private final VBox info = new VBox(16);
    private final PauseTransition statusTimeout = new PauseTransition(Duration.seconds(6));

    private int index;
    private CompletableFuture<Image> pending;

    ViewerView(Host host, VitralApi api, ImageLoader thumbnails, ImageLoader previews, ClientSettings settings,
               ZoneId zone, int startIndex) {
        this.host = host;
        this.api = api;
        this.thumbnails = thumbnails;
        this.previews = previews;
        this.settings = settings;
        this.zone = zone;

        BorderPane layout = new BorderPane();
        layout.setTop(topBar());
        layout.setCenter(stage());
        layout.setRight(info);
        getChildren().add(layout);
        getStyleClass().add("viewer");

        info.getStyleClass().add("viewer-info");
        info.setPrefWidth(INFO_WIDTH);
        info.setPadding(new Insets(20));
        info.visibleProperty().bind(infoToggle.selectedProperty());
        info.managedProperty().bind(infoToggle.selectedProperty());
        statusTimeout.setOnFinished(event -> status.setText(null));

        setFocusTraversable(true);
        // A filter, so the arrows reach the viewer even when one of its buttons has the focus.
        addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        show(startIndex);
    }

    /** Fades in and takes the keyboard. Call it once the viewer is on screen. */
    void open() {
        FadeTransition fade = new FadeTransition(Duration.millis(140), this);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        requestFocus();
    }

    private Node topBar() {
        Button close = new Button(null, new FontIcon(Feather.X));
        close.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        close.setTooltip(new Tooltip("Fechar (Esc)"));
        close.setOnAction(event -> close());

        title.getStyleClass().addAll(Styles.TEXT_BOLD, "viewer-title");
        subtitle.getStyleClass().add("viewer-subtitle");
        VBox names = new VBox(2, title, subtitle);

        status.getStyleClass().add("viewer-subtitle");

        infoToggle.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        infoToggle.setTooltip(new Tooltip("Detalhes (I)"));

        download.getStyleClass().add(Styles.FLAT);
        download.setOnAction(event -> downloadOriginal());

        HBox bar = new HBox(12, close, names, new Spacer(), status, infoToggle, download);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.getStyleClass().add("viewer-bar");
        return bar;
    }

    private Node stage() {
        StackPane stage = new StackPane();
        stage.setMinSize(0, 0);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(stage.widthProperty().subtract(2 * SIDE_ROOM));
        imageView.fitHeightProperty().bind(stage.heightProperty().subtract(32));
        spinner.setMaxSize(40, 40);
        notice.getStyleClass().add("viewer-notice");
        notice.setWrapText(true);
        notice.setMaxWidth(420);

        previous.setOnAction(event -> step(-1));
        next.setOnAction(event -> step(1));
        StackPane.setAlignment(previous, Pos.CENTER_LEFT);
        StackPane.setAlignment(next, Pos.CENTER_RIGHT);
        StackPane.setMargin(previous, new Insets(0, 0, 0, 16));
        StackPane.setMargin(next, new Insets(0, 16, 0, 0));

        stage.getChildren().addAll(imageView, notice, spinner, previous, next);
        return stage;
    }

    private void onKey(KeyEvent event) {
        switch (event.getCode()) {
            case LEFT -> step(-1);
            case RIGHT -> step(1);
            case ESCAPE -> close();
            case I -> infoToggle.setSelected(!infoToggle.isSelected());
            default -> {
                return;
            }
        }
        event.consume();
    }

    private void step(int delta) {
        int target = index + delta;
        if (target >= 0 && target < host.assets().size()) {
            show(target);
        } else if (target >= host.assets().size() && host.hasMore()) {
            // The next page is on its way; the button works again once it arrives.
            host.loadMore();
        }
    }

    private void show(int newIndex) {
        cancelPending();
        index = newIndex;
        List<Asset> assets = host.assets();
        Asset asset = assets.get(index);

        title.setText(asset.originalFileName());
        subtitle.setText(Formats.dateTime(asset.takenAt(), zone));
        fillInfo(asset);
        previous.setDisable(index == 0);
        next.setDisable(index == assets.size() - 1 && !host.hasMore());
        if (index >= assets.size() - LOAD_AHEAD) {
            host.loadMore();
        }

        showNotice(null);
        if (asset.thumbnailStatus() == ThumbnailStatus.FAILED) {
            showImage(null);
            showNotice("Não foi possível gerar uma prévia desta foto. O original ainda pode ser baixado.");
            return;
        }
        Image preview = previews.cached(asset.id());
        if (preview != null) {
            showImage(preview);
        } else {
            showImage(thumbnails.cached(asset.id()));
            loadPreview(asset);
        }
        prefetch(index + 1);
        prefetch(index - 1);
    }

    private void loadPreview(Asset asset) {
        if (asset.thumbnailStatus() != ThumbnailStatus.READY) {
            showNotice("A prévia desta foto ainda está sendo gerada. Tente de novo em instantes.");
            return;
        }
        spinner.setVisible(true);
        pending = previews.load(asset.id());
        pending.whenComplete((image, error) -> Platform.runLater(() -> {
            if (host.assets().get(index) != asset) {
                return;
            }
            spinner.setVisible(false);
            if (image != null) {
                showImage(image);
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof CancellationException) {
                return;
            }
            if (cause instanceof ApiException apiError && apiError.isUnauthorized()) {
                host.sessionExpired();
            } else if (cause instanceof ApiException apiError && "thumbnail_pending".equals(apiError.code())) {
                showNotice("A prévia desta foto ainda está sendo gerada. Tente de novo em instantes.");
            } else {
                showNotice(ErrorMessages.describe(cause));
            }
        }));
    }

    /** Starts loading a neighbor's preview, so moving to it is instant. */
    private void prefetch(int neighbor) {
        List<Asset> assets = host.assets();
        if (neighbor >= 0 && neighbor < assets.size() && assets.get(neighbor).thumbnailStatus() == ThumbnailStatus.READY) {
            previews.load(assets.get(neighbor).id());
        }
    }

    private void showImage(Image image) {
        imageView.setImage(image);
        spinner.setVisible(false);
    }

    private void showNotice(String text) {
        notice.setText(text);
        notice.setVisible(text != null);
    }

    private void fillInfo(Asset asset) {
        Label heading = new Label("Detalhes");
        heading.getStyleClass().addAll(Styles.TITLE_4, "viewer-title");
        info.getChildren().setAll(heading,
                infoRow("Nome", asset.originalFileName()),
                infoRow(dateLabel(asset.takenAtSource()), Formats.dateTime(asset.takenAt(), zone)),
                infoRow("Dimensões", Formats.dimensions(asset.width(), asset.height())),
                infoRow("Tamanho", Formats.fileSize(asset.sizeBytes())),
                infoRow("Formato", asset.mimeType()),
                infoRow("Enviada em", asset.uploadedAt() == null ? null : Formats.dateTime(asset.uploadedAt(), zone)));
    }

    /** Tells where the date came from, since a file or upload date may not be when the photo was taken. */
    private static String dateLabel(TakenAtSource source) {
        if (source == null) {
            return "Data";
        }
        return switch (source) {
            case EXIF -> "Tirada em";
            case FILE -> "Data do arquivo";
            case UPLOAD -> "Data do envio";
        };
    }

    private static Node infoRow(String label, String value) {
        Label caption = new Label(label);
        caption.getStyleClass().addAll(Styles.TEXT_SMALL, "viewer-subtitle");
        Label text = new Label(value == null ? "Desconhecido" : value);
        text.getStyleClass().add("viewer-value");
        text.setWrapText(true);
        return new VBox(2, caption, text);
    }

    private void downloadOriginal() {
        Asset asset = host.assets().get(index);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar original");
        chooser.setInitialDirectory(settings.downloadDirectory().toFile());
        chooser.setInitialFileName(asset.originalFileName());
        File chosen = chooser.showSaveDialog(getScene().getWindow());
        if (chosen == null) {
            requestFocus();
            return;
        }
        Path target = chosen.toPath();
        settings.rememberDownloadDirectory(target.getParent());
        download.setDisable(true);
        showStatus("Baixando " + asset.originalFileName() + "…", false);
        Background.run(() -> {
            api.downloadOriginal(asset.id(), target);
            return target;
        }, saved -> {
            download.setDisable(false);
            showStatus("Salvo em " + saved, true);
        }, error -> {
            download.setDisable(false);
            if (error instanceof ApiException apiError && apiError.isUnauthorized()) {
                host.sessionExpired();
                return;
            }
            showStatus("Não foi possível baixar: " + ErrorMessages.describe(error), true);
        });
        requestFocus();
    }

    private void showStatus(String text, boolean fadeOut) {
        statusTimeout.stop();
        status.setText(text);
        if (fadeOut) {
            statusTimeout.playFromStart();
        }
    }

    private void close() {
        cancelPending();
        statusTimeout.stop();
        host.closed(host.assets().get(index));
    }

    private void cancelPending() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
        spinner.setVisible(false);
    }

    private static Button navigationButton(Feather icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, "viewer-nav");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }
}
