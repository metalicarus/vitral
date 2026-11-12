package com.genosha.vitral.client.ui;

import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.api.ApiException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * The administrators' area, laid over the gallery: the server's accounts and its watched
 * folders. Only shown to administrators; the server checks the role on every call anyway.
 */
final class AdminView extends BorderPane {

    private final UsersPane users;
    private final FoldersPane folders;
    private boolean libraryChanged;

    /**
     * @param onClose          called when the user goes back to the gallery, with whether the
     *                         photos in it may have changed (a folder was added or removed)
     * @param onSessionExpired called when the server no longer accepts the session
     */
    AdminView(Session session, Consumer<Boolean> onClose, Runnable onSessionExpired) {
        Consumer<Throwable> onError = error -> {
            if (error instanceof ApiException api && api.isUnauthorized()) {
                onSessionExpired.run();
            }
        };
        folders = new FoldersPane(session.api(), onError, () -> libraryChanged = true);
        users = new UsersPane(session.api(), onError, folders::reloadOwners);

        Button back = new Button("Voltar à galeria", new FontIcon(Feather.ARROW_LEFT));
        back.getStyleClass().add(Styles.FLAT);
        back.setOnAction(event -> onClose.accept(libraryChanged));
        Label title = new Label("Administração", new FontIcon(Feather.SETTINGS));
        title.getStyleClass().add(Styles.TITLE_3);
        HBox header = new HBox(16, back, title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.getStyleClass().add("app-header");

        Tab usersTab = new Tab("Usuários", users);
        usersTab.setGraphic(new FontIcon(Feather.USERS));
        Tab foldersTab = new Tab("Pastas monitoradas", folders);
        foldersTab.setGraphic(new FontIcon(Feather.FOLDER));
        TabPane tabs = new TabPane(usersTab, foldersTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // The folders' scan status is refreshed only while it is on screen.
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> folders.setActive(tab == foldersTab));

        setTop(header);
        setCenter(tabs);
        getStyleClass().add("admin-view");
    }

    /** Stops refreshing in the background. Called when the view goes away for any reason. */
    void dispose() {
        folders.dispose();
    }
}
