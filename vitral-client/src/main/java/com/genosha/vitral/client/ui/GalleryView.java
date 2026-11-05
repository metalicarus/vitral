package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.Models.AssetPage;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The user's photos, newest first, grouped by month. More photos load as the user scrolls
 * toward the end, one page at a time.
 */
public class GalleryView extends BorderPane {

    private static final int PAGE_SIZE = 200;
    /** Starts loading the next page when the user is this many lines from the end. */
    private static final int LOAD_AHEAD_ROWS = 6;
    private static final double TARGET_TILE_SIZE = 180;
    private static final double GAP = 4;
    private static final double SIDE_PADDING = 16;
    /** Room left for the vertical scroll bar, so the last column never hides under it. */
    private static final double SCROLLBAR_ALLOWANCE = 14;
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"));

    private final Session session;
    private final Runnable onSessionExpired;
    private final ThumbnailLoader thumbnails;
    private final ZoneId zone = ZoneId.systemDefault();

    private final ListView<GalleryRow> list = new ListView<>();
    private final StackPane content = new StackPane();
    private final ProgressBar loadingMore = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);

    private final List<Asset> assets = new ArrayList<>();
    private String nextCursor;
    private boolean loading;
    /** Bumped on every reload, so answers to requests made before it are ignored. */
    private int generation;
    private int columns;
    private double tileSize = TARGET_TILE_SIZE;

    /**
     * @param onLogout         called when the user clicks "Sair"
     * @param onSessionExpired called when the server no longer accepts the session
     */
    public GalleryView(Session session, Runnable onLogout, Runnable onSessionExpired) {
        this.session = session;
        this.onSessionExpired = onSessionExpired;
        this.thumbnails = new ThumbnailLoader(session.api());

        list.setCellFactory(view -> new RowCell());
        list.getStyleClass().addAll("gallery", Tweaks.EDGE_TO_EDGE);
        list.setFocusTraversable(false);
        list.widthProperty().addListener((obs, old, width) -> updateColumns(width.doubleValue()));

        loadingMore.setMaxWidth(Double.MAX_VALUE);
        loadingMore.getStyleClass().add(Styles.SMALL);
        showLoadingMore(false);

        setTop(header(onLogout));
        setCenter(content);
        setBottom(loadingMore);
        getStyleClass().add("gallery-view");
        reload();
    }

    /** Stops loading thumbnails and frees their memory. Call it when leaving this view. */
    public void dispose() {
        thumbnails.close();
    }

    private Node header(Runnable onLogout) {
        Label brand = new Label("Vitral", new FontIcon(Feather.APERTURE));
        brand.getStyleClass().addAll(Styles.TITLE_3, "brand");

        Label user = new Label(session.user().displayName(), new FontIcon(Feather.USER));
        user.getStyleClass().add(Styles.TEXT_MUTED);
        user.setTooltip(new Tooltip(session.user().email()));

        Button refresh = new Button(null, new FontIcon(Feather.REFRESH_CW));
        refresh.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        refresh.setTooltip(new Tooltip("Atualizar"));
        refresh.setOnAction(event -> reload());

        Button logout = new Button("Sair", new FontIcon(Feather.LOG_OUT));
        logout.getStyleClass().add(Styles.FLAT);
        logout.setOnAction(event -> onLogout.run());

        HBox header = new HBox(12, brand, new Spacer(), user, refresh, logout);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, SIDE_PADDING, 10, SIDE_PADDING));
        header.getStyleClass().add("app-header");
        return header;
    }

    /** Starts over from the newest photo. */
    private void reload() {
        generation++;
        assets.clear();
        nextCursor = null;
        loading = false;
        list.getItems().clear();
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        content.getChildren().setAll(spinner);
        loadPage(null);
    }

    /** Loads the next page, if there is one and none is on its way. */
    private void loadMore() {
        if (!loading && nextCursor != null) {
            loadPage(nextCursor);
        }
    }

    private void loadPage(String cursor) {
        loading = true;
        int requestGeneration = generation;
        if (cursor != null) {
            showLoadingMore(true);
        }
        Background.run(() -> session.api().listAssets(PAGE_SIZE, cursor), page -> {
            if (requestGeneration == generation) {
                pageLoaded(page);
            }
        }, error -> {
            if (requestGeneration == generation) {
                pageFailed(error);
            }
        });
    }

    private void pageLoaded(AssetPage page) {
        loading = false;
        showLoadingMore(false);
        assets.addAll(page.items());
        nextCursor = page.nextCursor();
        if (assets.isEmpty()) {
            content.getChildren().setAll(emptyState());
            return;
        }
        if (content.getChildren().size() != 1 || content.getChildren().getFirst() != list) {
            content.getChildren().setAll(list);
        }
        relayout(false);
    }

    private void pageFailed(Throwable error) {
        loading = false;
        showLoadingMore(false);
        if (error instanceof ApiException api && api.isUnauthorized()) {
            onSessionExpired.run();
            return;
        }
        if (assets.isEmpty()) {
            content.getChildren().setAll(errorState(ErrorMessages.describe(error)));
        }
        // With photos already on screen, the user can keep browsing; scrolling down tries again.
    }

    /** Recomputes how many photos fit per line when the window is resized. */
    private void updateColumns(double width) {
        double available = width - 2 * SIDE_PADDING - SCROLLBAR_ALLOWANCE;
        if (available <= 0) {
            return;
        }
        int newColumns = Math.max(1, (int) ((available + GAP) / (TARGET_TILE_SIZE + GAP)));
        double newTileSize = Math.floor((available - GAP * (newColumns - 1)) / newColumns);
        boolean columnsChanged = newColumns != columns;
        boolean sizeChanged = newTileSize != tileSize;
        columns = newColumns;
        tileSize = newTileSize;
        if (columnsChanged) {
            relayout(true);
        } else if (sizeChanged) {
            list.refresh();
        }
    }

    /**
     * Rebuilds the lines from the loaded photos. After a new page only the lines from the
     * first change onwards are replaced, so the list does not jump; after a resize the photo
     * that was at the top stays in view.
     */
    private void relayout(boolean columnsChanged) {
        if (columns == 0) {
            return;
        }
        List<GalleryRow> next = GalleryRow.layout(assets, columns, zone);
        if (columnsChanged) {
            UUID anchor = firstVisibleAsset();
            list.getItems().setAll(next);
            if (anchor != null) {
                list.scrollTo(Math.max(0, rowContaining(next, anchor) - 1));
            }
            return;
        }
        ObservableList<GalleryRow> current = list.getItems();
        int from = GalleryRow.firstDifference(current, next);
        if (from < current.size()) {
            current.remove(from, current.size());
        }
        current.addAll(next.subList(from, next.size()));
    }

    private UUID firstVisibleAsset() {
        if (!(list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) || flow.getFirstVisibleCell() == null) {
            return null;
        }
        List<GalleryRow> rows = list.getItems();
        for (int i = flow.getFirstVisibleCell().getIndex(); i >= 0 && i < rows.size(); i++) {
            if (rows.get(i) instanceof GalleryRow.Photos photos) {
                return photos.assets().getFirst().id();
            }
        }
        return null;
    }

    private static int rowContaining(List<GalleryRow> rows, UUID assetId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof GalleryRow.Photos photos
                    && photos.assets().stream().anyMatch(asset -> asset.id().equals(assetId))) {
                return i;
            }
        }
        return 0;
    }

    private void showLoadingMore(boolean visible) {
        loadingMore.setVisible(visible);
        loadingMore.setManaged(visible);
    }

    private Node emptyState() {
        return centeredState(Feather.IMAGE, "Nenhuma foto ainda",
                "As fotos enviadas ao servidor aparecem aqui, da mais recente para a mais antiga.", null);
    }

    private Node errorState(String message) {
        Button retry = new Button("Tentar de novo", new FontIcon(Feather.REFRESH_CW));
        retry.setOnAction(event -> reload());
        return centeredState(Feather.CLOUD_OFF, "Não foi possível carregar as fotos", message, retry);
    }

    private static Node centeredState(Feather icon, String title, String description, Node action) {
        FontIcon graphic = new FontIcon(icon);
        graphic.getStyleClass().add("state-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(Styles.TITLE_3);
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add(Styles.TEXT_MUTED);
        descriptionLabel.setWrapText(true);
        VBox box = new VBox(10, graphic, titleLabel, descriptionLabel);
        if (action != null) {
            box.getChildren().add(action);
        }
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(420);
        return box;
    }

    private static String monthTitle(YearMonth month) {
        String text = MONTH.format(month);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Draws one line of the gallery, reusing its tiles as the user scrolls. */
    private final class RowCell extends ListCell<GalleryRow> {

        private final Label monthLabel = new Label();
        private final HBox photos = new HBox(GAP);
        private final List<TileView> tiles = new ArrayList<>();

        RowCell() {
            monthLabel.getStyleClass().addAll(Styles.TITLE_4, "month-header");
            photos.setPadding(new Insets(0, 0, GAP, 0));
        }

        @Override
        protected void updateItem(GalleryRow row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            switch (row) {
                case GalleryRow.MonthHeader header -> {
                    monthLabel.setText(monthTitle(header.month()));
                    setGraphic(monthLabel);
                }
                case GalleryRow.Photos line -> {
                    List<Asset> rowAssets = line.assets();
                    while (tiles.size() < rowAssets.size()) {
                        tiles.add(new TileView(thumbnails, zone));
                    }
                    for (int i = 0; i < rowAssets.size(); i++) {
                        tiles.get(i).show(rowAssets.get(i), tileSize);
                    }
                    photos.getChildren().setAll(tiles.subList(0, rowAssets.size()));
                    setGraphic(photos);
                }
            }
            if (getIndex() >= getListView().getItems().size() - LOAD_AHEAD_ROWS) {
                loadMore();
            }
        }
    }
}
