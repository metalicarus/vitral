package com.genosha.vitral.client;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.genosha.vitral.client.ui.Background;
import com.genosha.vitral.client.ui.GalleryView;
import com.genosha.vitral.client.ui.Links;
import com.genosha.vitral.client.ui.LoginView;
import com.genosha.vitral.client.ui.Session;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/** The desktop client: a login screen, then the user's gallery. */
public class VitralClient extends Application {

    private static final double WIDTH = 1180;
    private static final double HEIGHT = 780;

    private final ClientSettings settings = new ClientSettings();
    private Scene scene;
    private GalleryView gallery;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Links.setOpener(getHostServices()::showDocument);
        followSystemColorScheme();
        scene = new Scene(new LoginView(settings, null, this::showGallery), WIDTH, HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(VitralClient.class.getResource("vitral.css")).toExternalForm());
        stage.setTitle("Vitral");
        stage.setMinWidth(480);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        closeGallery();
    }

    private void showGallery(Session session) {
        gallery = new GalleryView(session, settings,
                () -> logout(session),
                () -> showLogin("Sua sessão expirou. Entre de novo."));
        show(gallery);
    }

    private void logout(Session session) {
        // Ending the session on the server is best effort: the user is logged out locally either way.
        Background.run(() -> {
            session.api().logout();
            return null;
        }, ignored -> { }, ignored -> { });
        showLogin(null);
    }

    private void showLogin(String notice) {
        closeGallery();
        show(new LoginView(settings, notice, this::showGallery));
    }

    private void show(Parent root) {
        scene.setRoot(root);
    }

    private void closeGallery() {
        if (gallery != null) {
            gallery.dispose();
            gallery = null;
        }
    }

    /** Uses the light or dark Primer theme to match the operating system, and follows it if it changes. */
    private static void followSystemColorScheme() {
        Platform.Preferences preferences = Platform.getPreferences();
        applyTheme(preferences.getColorScheme());
        preferences.colorSchemeProperty().addListener((obs, old, scheme) -> applyTheme(scheme));
    }

    private static void applyTheme(ColorScheme scheme) {
        Application.setUserAgentStylesheet(scheme == ColorScheme.DARK
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }
}
