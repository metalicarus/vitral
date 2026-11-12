package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.genosha.vitral.client.ClientSettings;
import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.Models.AssetPage;
import com.genosha.vitral.client.api.Models.HashCheckResult;
import com.genosha.vitral.client.api.Models.UploadResult;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The user's photos, newest first, grouped by month. More photos load as the user scrolls
 * toward the end, one page at a time. Clicking a photo opens it in the {@link ViewerView},
 * laid over the gallery. Photos are added with the "Enviar fotos" button or by dropping
 * files and folders on the window; an {@link UploadJob} sends them.
 */
public class GalleryView extends StackPane {

    private static final int PAGE_SIZE = 200;
    /** Starts loading the next page when the user is this many lines from the end. */
    private static final int LOAD_AHEAD_ROWS = 6;
    private static final double TARGET_TILE_SIZE = 180;
    private static final double GAP = 4;
    private static final double SIDE_PADDING = 16;
    /** Room left for the vertical scroll bar, so the last column never hides under it. */
    private static final double SCROLLBAR_ALLOWANCE = 14;

    private final Session session;
    private final ClientSettings settings;
    private final Runnable onSessionExpired;
    private final ImageLoader thumbnails;
    /** Few previews stay in memory: at 1440 px each one takes about 6 MB once decoded. */
    private final ImageLoader previews;
    private final ZoneId zone = ZoneId.systemDefault();

    private final BorderPane layout = new BorderPane();
    private final ListView<GalleryRow> list = new ListView<>();
    private final StackPane content = new StackPane();
    private final ProgressBar loadingMore = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
    private final UploadBar uploadBar = new UploadBar(this::cancelUpload);
    private final MenuButton uploadButton = new MenuButton("Enviar fotos", new FontIcon(Feather.UPLOAD));
    private final Node dropHint = dropHint();

    private final List<Asset> assets = new ArrayList<>();
    private String nextCursor;
    private boolean loading;
    /** Bumped on every reload, so answers to requests made before it are ignored. */
    private int generation;
    private int columns;
    private double tileSize = TARGET_TILE_SIZE;
    private ViewerView viewer;
    private AdminView admin;
    private UploadJob upload;
    private Thread uploadThread;

    /**
     * @param onLogout         called when the user clicks "Sair"
     * @param onSessionExpired called when the server no longer accepts the session
     */
    public GalleryView(Session session, ClientSettings settings, Runnable onLogout, Runnable onSessionExpired) {
        this.session = session;
        this.settings = settings;
        this.onSessionExpired = onSessionExpired;
        this.thumbnails = new ImageLoader("thumbnails", session.api()::thumbnail, 4, 500);
        this.previews = new ImageLoader("previews", session.api()::preview, 2, 12);

        list.setCellFactory(view -> new RowCell());
        list.getStyleClass().addAll("gallery", Tweaks.EDGE_TO_EDGE);
        list.setFocusTraversable(false);
        list.widthProperty().addListener((obs, old, width) -> updateColumns(width.doubleValue()));

        loadingMore.setMaxWidth(Double.MAX_VALUE);
        loadingMore.getStyleClass().add(Styles.SMALL);
        showLoadingMore(false);

        layout.setTop(header(onLogout));
        layout.setCenter(content);
        layout.setBottom(new VBox(uploadBar, loadingMore));
        getChildren().addAll(layout, dropHint);
        getStyleClass().add("gallery-view");
        acceptDroppedFiles();
        reload();
    }

    /** Stops uploads and image loading, and frees memory. Call it when leaving this view. */
    public void dispose() {
        cancelUpload();
        if (admin != null) {
            admin.dispose();
        }
        thumbnails.close();
        previews.close();
    }

    /** Files and folders dropped anywhere on the gallery are uploaded. */
    private void acceptDroppedFiles() {
        setOnDragOver(event -> {
            if (canUpload() && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragEntered(event -> dropHint.setVisible(canUpload() && event.getDragboard().hasFiles()));
        setOnDragExited(event -> dropHint.setVisible(false));
        setOnDragDropped(event -> {
            dropHint.setVisible(false);
            boolean accepted = canUpload() && event.getDragboard().hasFiles();
            if (accepted) {
                startUpload(event.getDragboard().getFiles().stream().map(File::toPath).toList());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private Node dropHint() {
        FontIcon icon = new FontIcon(Feather.UPLOAD_CLOUD);
        icon.getStyleClass().add("state-icon");
        Label title = new Label("Solte para enviar ao Vitral");
        title.getStyleClass().add(Styles.TITLE_3);
        Label description = new Label("Fotos e pastas; o que o servidor já tem não é enviado de novo.");
        description.getStyleClass().add(Styles.TEXT_MUTED);
        VBox box = new VBox(10, icon, title, description);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("drop-hint");
        box.setMouseTransparent(true);
        box.setVisible(false);
        return box;
    }

    private boolean canUpload() {
        return upload == null && viewer == null && admin == null;
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher fotos para enviar");
        chooser.setInitialDirectory(settings.uploadDirectory().toFile());
        List<String> patterns = new ArrayList<>();
        for (String extension : UploadJob.PHOTO_EXTENSIONS) {
            // Some file dialogs match extensions case-sensitively.
            patterns.add("*." + extension);
            patterns.add("*." + extension.toUpperCase(Locale.ROOT));
        }
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fotos (JPEG, PNG, HEIC, WebP)", patterns),
                new FileChooser.ExtensionFilter("Todos os arquivos", "*"));
        List<File> chosen = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (chosen != null && !chosen.isEmpty()) {
            settings.rememberUploadDirectory(chosen.getFirst().toPath().getParent());
            startUpload(chosen.stream().map(File::toPath).toList());
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolher uma pasta para enviar");
        chooser.setInitialDirectory(settings.uploadDirectory().toFile());
        File chosen = chooser.showDialog(getScene().getWindow());
        if (chosen != null) {
            Path folder = chosen.toPath();
            settings.rememberUploadDirectory(folder.getParent() != null ? folder.getParent() : folder);
            startUpload(List.of(folder));
        }
    }

    private void startUpload(List<Path> selection) {
        if (!canUpload()) {
            return;
        }
        UploadJob.Server server = new UploadJob.Server() {
            @Override
            public HashCheckResult check(List<String> hashes) throws IOException {
                return session.api().check(hashes);
            }

            @Override
            public UploadResult upload(Path file, Instant fileModifiedAt) throws IOException {
                return session.api().upload(file, fileModifiedAt);
            }
        };
        AtomicLong lastReport = new AtomicLong();
        UploadJob job = new UploadJob(server, selection, (phase, done, total, fileName) -> {
            // Hashing many small files reports thousands of steps a second; the screen needs a few.
            long now = System.nanoTime();
            long last = lastReport.get();
            if (now - last > 50_000_000L || done == 0) {
                lastReport.set(now);
                Platform.runLater(() -> {
                    if (upload != null) {
                        uploadBar.showProgress(phase, done, total, fileName);
                    }
                });
            }
        });
        upload = job;
        uploadButton.setDisable(true);
        uploadBar.showProgress(UploadJob.Phase.COLLECTING, 0, 0, null);
        uploadThread = Thread.ofVirtual().name("vitral-upload").start(() -> {
            try {
                UploadJob.Summary summary = job.run();
                Platform.runLater(() -> uploadFinished(job, summary));
            } catch (ApiException expired) {
                Platform.runLater(onSessionExpired);
            } catch (RuntimeException e) {
                Platform.runLater(() -> uploadFinished(job, new UploadJob.Summary(0, 0, 0,
                        List.of(new UploadJob.Failure(Path.of("—"), ErrorMessages.describe(e))), false)));
            }
        });
    }

    private void cancelUpload() {
        if (upload != null) {
            upload.cancel();
            uploadThread.interrupt();
        }
    }

    private void uploadFinished(UploadJob job, UploadJob.Summary summary) {
        if (upload != job) {
            return;
        }
        upload = null;
        uploadThread = null;
        uploadButton.setDisable(false);
        uploadBar.showSummary(summary, () -> showFailures(summary.failures()));
        if (summary.uploaded() > 0) {
            reload();
        }
    }

    private void showFailures(List<UploadJob.Failure> failures) {
        TextArea list = new TextArea(failures.stream()
                .map(failure -> failure.file().getFileName() + " — " + failure.reason())
                .collect(Collectors.joining("\n")));
        list.setEditable(false);
        list.setWrapText(true);
        list.setPrefRowCount(Math.min(12, failures.size() + 1));
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(getScene().getWindow());
        alert.setTitle("Fotos não enviadas");
        alert.setHeaderText(Formats.count(failures.size(), "foto não foi enviada", "fotos não foram enviadas"));
        alert.getDialogPane().setContent(list);
        alert.showAndWait();
    }

    private void openAdmin() {
        if (admin != null || viewer != null) {
            return;
        }
        admin = new AdminView(session, this::closeAdmin, onSessionExpired);
        getChildren().add(admin);
    }

    /** @param libraryChanged whether a watched folder was added or removed, which changes the photos shown */
    private void closeAdmin(boolean libraryChanged) {
        getChildren().remove(admin);
        admin.dispose();
        admin = null;
        if (libraryChanged) {
            reload();
        }
    }

    private void openViewer(Asset asset) {
        int index = indexOf(asset.id());
        if (index < 0 || viewer != null || admin != null) {
            return;
        }
        viewer = new ViewerView(new ViewerHost(), session.api(), thumbnails, previews, settings, zone, index);
        getChildren().add(viewer);
        viewer.open();
    }

    /** Lets the viewer walk through the loaded photos and ask for more. */
    private final class ViewerHost implements ViewerView.Host {

        @Override
        public List<Asset> assets() {
            return assets;
        }

        @Override
        public boolean hasMore() {
            return nextCursor != null;
        }

        @Override
        public void loadMore() {
            GalleryView.this.loadMore();
        }

        @Override
        public void closed(Asset current) {
            getChildren().remove(viewer);
            viewer = null;
            revealRowOf(current.id());
        }

        @Override
        public void sessionExpired() {
            onSessionExpired.run();
        }
    }

    /** After the viewer closes, scrolls the gallery to the last photo seen there, unless it is already on screen. */
    private void revealRowOf(UUID assetId) {
        int row = rowContaining(list.getItems(), assetId);
        if (list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow
                && flow.getFirstVisibleCell() != null && flow.getLastVisibleCell() != null
                && row > flow.getFirstVisibleCell().getIndex() && row < flow.getLastVisibleCell().getIndex()) {
            return;
        }
        list.scrollTo(Math.max(0, row - 1));
    }

    private int indexOf(UUID assetId) {
        for (int i = 0; i < assets.size(); i++) {
            if (assets.get(i).id().equals(assetId)) {
                return i;
            }
        }
        return -1;
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

        MenuItem files = new MenuItem("Escolher fotos…", new FontIcon(Feather.IMAGE));
        files.setOnAction(event -> chooseFiles());
        MenuItem folder = new MenuItem("Escolher uma pasta…", new FontIcon(Feather.FOLDER));
        folder.setOnAction(event -> chooseFolder());
        uploadButton.getItems().setAll(files, folder);
        uploadButton.getStyleClass().add(Styles.ACCENT);
        uploadButton.setTooltip(new Tooltip("Também dá para arrastar fotos e pastas para esta janela"));

        HBox header = new HBox(12, brand, new Spacer(), uploadButton, user, refresh);
        if (session.user().isAdmin()) {
            Button settings = new Button(null, new FontIcon(Feather.SETTINGS));
            settings.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
            settings.setTooltip(new Tooltip("Administração: contas e pastas monitoradas"));
            settings.setOnAction(event -> openAdmin());
            header.getChildren().add(settings);
        }
        header.getChildren().add(logout);
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
                "Use “Enviar fotos” ou arraste fotos e pastas para esta janela. Elas aparecem aqui,"
                        + " da mais recente para a mais antiga.", null);
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
                    monthLabel.setText(Formats.month(header.month()));
                    setGraphic(monthLabel);
                }
                case GalleryRow.Photos line -> {
                    List<Asset> rowAssets = line.assets();
                    while (tiles.size() < rowAssets.size()) {
                        tiles.add(new TileView(thumbnails, zone, GalleryView.this::openViewer));
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
