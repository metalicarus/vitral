package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.Models.ThumbnailStatus;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * One square photo of the gallery. The thumbnail is cropped to fill the square, as in most
 * photo apps; while it loads, or when there is none, an icon stands in for it.
 */
final class TileView extends StackPane {

    private static final double CORNER_RADIUS = 6;
    private static final Duration FIRST_RETRY = Duration.seconds(2);
    private static final Duration MAX_RETRY = Duration.seconds(30);

    private final ImageLoader loader;
    private final ZoneId zone;
    private final ImageView imageView = new ImageView();
    private final FontIcon placeholder = new FontIcon();
    private final Tooltip tooltip = new Tooltip();
    private final Rectangle clip = new Rectangle();
    private final PauseTransition retry = new PauseTransition();

    private Asset asset;
    private double size;
    private CompletableFuture<Image> pending;

    /** @param onOpen called with the photo when the user clicks it */
    TileView(ImageLoader loader, ZoneId zone, Consumer<Asset> onOpen) {
        this.loader = loader;
        this.zone = zone;
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && asset != null) {
                onOpen.accept(asset);
            }
        });
        imageView.setSmooth(true);
        imageView.setPreserveRatio(false);
        placeholder.getStyleClass().add("tile-placeholder");
        clip.setArcWidth(CORNER_RADIUS * 2);
        clip.setArcHeight(CORNER_RADIUS * 2);
        setClip(clip);
        getChildren().addAll(placeholder, imageView);
        getStyleClass().add("tile");
        Tooltip.install(this, tooltip);
    }

    /** Shows {@code asset} in a square of {@code size} pixels, reusing this node for a different photo if needed. */
    void show(Asset asset, double size) {
        resize(size);
        if (this.asset != null && this.asset.id().equals(asset.id())) {
            return;
        }
        cancelPending();
        this.asset = asset;
        tooltip.setText(asset.originalFileName() + "\n" + Formats.dateTime(asset.takenAt(), zone));
        imageView.setImage(null);
        imageView.setOpacity(1);

        if (asset.thumbnailStatus() == ThumbnailStatus.FAILED) {
            showPlaceholder(Feather.ALERT_TRIANGLE);
            return;
        }
        Image cached = loader.cached(asset.id());
        if (cached != null) {
            showImage(cached, false);
            return;
        }
        // A photo the list called pending may be ready by now, so it is asked for all the same.
        showPlaceholder(asset.thumbnailStatus() == ThumbnailStatus.READY ? Feather.IMAGE : Feather.CLOCK);
        load(asset, FIRST_RETRY);
    }

    /**
     * Asks for the thumbnail. While the server is still generating it, asks again after
     * {@code retryDelay}, then after twice as long, up to {@link #MAX_RETRY}; this way photos
     * just uploaded fill in by themselves. Stops as soon as the tile shows another photo.
     */
    private void load(Asset requested, Duration retryDelay) {
        pending = loader.load(requested.id());
        pending.whenComplete((image, error) -> Platform.runLater(() -> {
            if (this.asset != requested) {
                return;
            }
            if (image != null) {
                showImage(image, true);
            } else if (unwrap(error) instanceof ApiException api && "thumbnail_pending".equals(api.code())) {
                showPlaceholder(Feather.CLOCK);
                retry.setDuration(retryDelay);
                retry.setOnFinished(event -> {
                    if (this.asset == requested) {
                        load(requested, retryDelay.multiply(2).lessThan(MAX_RETRY) ? retryDelay.multiply(2) : MAX_RETRY);
                    }
                });
                retry.playFromStart();
            } else if (!(unwrap(error) instanceof CancellationException)) {
                showPlaceholder(Feather.ALERT_TRIANGLE);
            }
        }));
    }

    private void resize(double newSize) {
        if (newSize == size) {
            return;
        }
        size = newSize;
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        clip.setWidth(size);
        clip.setHeight(size);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
    }

    /** Crops the image's centered square, so wide and tall photos both fill the tile. */
    private void showImage(Image image, boolean fadeIn) {
        double side = Math.min(image.getWidth(), image.getHeight());
        imageView.setViewport(new Rectangle2D((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side));
        imageView.setImage(image);
        placeholder.setVisible(false);
        if (fadeIn) {
            FadeTransition fade = new FadeTransition(Duration.millis(180), imageView);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }

    private void showPlaceholder(Feather icon) {
        placeholder.setIconCode(icon);
        placeholder.setVisible(true);
    }

    private void cancelPending() {
        retry.stop();
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
