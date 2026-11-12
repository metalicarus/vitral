package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Message;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.ClientSettings;
import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.LoginResult;
import com.genosha.vitral.client.api.VitralApi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * Asks for the server address, e-mail and password, and logs in. When the server has no
 * account yet, the same form creates its first one, an administrator, asking also for a
 * name and for the password again.
 */
public class LoginView extends StackPane {

    private static final double CARD_WIDTH = 380;

    private final ClientSettings settings;
    private final Consumer<Session> onLogin;

    private final TextField server = new TextField();
    private final TextField email = new TextField();
    private final PasswordTextField password = new PasswordTextField();
    private final TextField displayName = new TextField();
    private final PasswordTextField confirmation = new PasswordTextField();
    private final VBox setupFields;
    private final Button submit = new Button("Entrar");
    private final VBox messages = new VBox();
    /** Whether the form creates the first administrator instead of logging in. */
    private boolean setupMode;

    /** @param notice shown above the form, for example why the user was sent back here; may be null */
    public LoginView(ClientSettings settings, String notice, Consumer<Session> onLogin) {
        this.settings = settings;
        this.onLogin = onLogin;

        Label title = new Label("Vitral", new FontIcon(Feather.APERTURE));
        title.getStyleClass().addAll(Styles.TITLE_1, "brand");
        Label subtitle = new Label("Suas fotos, no seu servidor.");
        subtitle.getStyleClass().add(Styles.TEXT_MUTED);
        VBox header = new VBox(4, title, subtitle);

        server.setText(settings.serverAddress());
        server.setPromptText("http://localhost:8080");
        email.setText(settings.email());
        email.setPromptText("voce@exemplo.com");
        password.setPromptText("Senha");
        password.setRight(revealToggle());
        displayName.setPromptText("Como seu nome aparece no Vitral");
        confirmation.setPromptText("A mesma senha de novo");
        setupFields = new VBox(12, field("Seu nome", displayName), field("Confirme a senha", confirmation));
        showSetupFields(false);
        // Another address may be a server that is already set up.
        server.textProperty().addListener((obs, old, text) -> {
            if (setupMode) {
                leaveSetupMode();
            }
        });

        submit.getStyleClass().add(Styles.ACCENT);
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(event -> login());

        VBox form = new VBox(12,
                field("Servidor", server),
                field("E-mail", email),
                field("Senha", password),
                setupFields,
                messages,
                submit);

        Card card = new Card();
        card.setHeader(header);
        card.setBody(form);
        card.setMaxSize(CARD_WIDTH, USE_PREF_SIZE);
        card.getStyleClass().add(Styles.ELEVATED_2);

        if (notice != null) {
            showMessage(new Message("Atenção", notice, new FontIcon(Feather.INFO)), Styles.WARNING);
        }
        getChildren().add(card);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(24));
        getStyleClass().add("login-view");

        // Puts the cursor where the user most likely has to type.
        sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                (email.getText().isEmpty() ? email : password).requestFocus();
            }
        });
    }

    private void login() {
        if (setupMode) {
            createAdministrator();
            return;
        }
        String address = server.getText();
        String emailValue = email.getText().strip();
        String passwordValue = password.getPassword();
        if (address.isBlank() || emailValue.isEmpty() || passwordValue.isEmpty()) {
            showError("Preencha o servidor, o e-mail e a senha.");
            return;
        }
        setBusy(true);
        Background.run(() -> {
            VitralApi api = new VitralApi(address);
            if (api.serverInfo().setupRequired()) {
                return new Attempt(api, null);
            }
            LoginResult result = api.login(emailValue, passwordValue, deviceName());
            return new Attempt(api, result);
        }, attempt -> {
            setBusy(false);
            if (attempt.result() == null) {
                enterSetupMode();
                return;
            }
            settings.remember(attempt.api().baseUri().toString(), emailValue);
            onLogin.accept(new Session(attempt.api(), attempt.result().user()));
        }, error -> {
            setBusy(false);
            showError(ErrorMessages.describe(error));
            password.requestFocus();
        });
    }

    /** Turns the form into the first-run setup, keeping what was already typed. */
    private void enterSetupMode() {
        setupMode = true;
        showSetupFields(true);
        submit.setText("Criar administrador e entrar");
        showMessage(new Message("Servidor novo",
                "Este servidor ainda não tem contas. Crie a conta de administrador; com ela você cria"
                        + " depois as contas da família.", new FontIcon(Feather.INFO)), Styles.ACCENT);
        (displayName.getText().isBlank() ? displayName : confirmation).requestFocus();
    }

    private void leaveSetupMode() {
        setupMode = false;
        showSetupFields(false);
        submit.setText("Entrar");
        messages.getChildren().clear();
    }

    private void showSetupFields(boolean shown) {
        setupFields.setVisible(shown);
        setupFields.setManaged(shown);
    }

    private void createAdministrator() {
        String address = server.getText();
        String emailValue = email.getText().strip();
        String nameValue = displayName.getText().strip();
        String passwordValue = password.getPassword();
        if (emailValue.isEmpty() || nameValue.isEmpty()) {
            showError("Preencha o e-mail e o seu nome.");
            return;
        }
        if (passwordValue.length() < 8) {
            showError("A senha precisa ter pelo menos 8 caracteres.");
            return;
        }
        if (!passwordValue.equals(confirmation.getPassword())) {
            showError("As duas senhas não são iguais.");
            confirmation.requestFocus();
            return;
        }
        setBusy(true);
        Background.run(() -> {
            VitralApi api = new VitralApi(address);
            return new Attempt(api, api.setup(emailValue, nameValue, passwordValue));
        }, attempt -> {
            setBusy(false);
            settings.remember(attempt.api().baseUri().toString(), emailValue);
            onLogin.accept(new Session(attempt.api(), attempt.result().user()));
        }, error -> {
            setBusy(false);
            if (error instanceof ApiException api && "setup_done".equals(api.code())) {
                // Someone else set the server up in the meantime: back to a normal login.
                leaveSetupMode();
            }
            showError(ErrorMessages.describe(error));
        });
    }

    /** The result of a login attempt: no result means the server still needs its first-run setup. */
    private record Attempt(VitralApi api, LoginResult result) {
    }

    /** How this device shows up in the user's list of sessions. */
    private static String deviceName() {
        String host = System.getenv().getOrDefault("HOSTNAME", "");
        String name = "Vitral para desktop (" + System.getProperty("os.name") + (host.isBlank() ? "" : ", " + host) + ")";
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private void setBusy(boolean busy) {
        server.setDisable(busy);
        email.setDisable(busy);
        password.setDisable(busy);
        setupFields.setDisable(busy);
        submit.setDisable(busy);
        if (busy) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(16, 16);
            submit.setGraphic(spinner);
            submit.setText(setupMode ? "Criando…" : "Entrando…");
            messages.getChildren().clear();
        } else {
            submit.setGraphic(null);
            submit.setText(setupMode ? "Criar administrador e entrar" : "Entrar");
        }
    }

    private void showError(String text) {
        showMessage(new Message(null, text, new FontIcon(Feather.ALERT_CIRCLE)), Styles.DANGER);
    }

    private void showMessage(Message message, String style) {
        message.getStyleClass().add(style);
        messages.getChildren().setAll(message);
    }

    private FontIcon revealToggle() {
        FontIcon icon = new FontIcon(Feather.EYE_OFF);
        icon.setCursor(Cursor.HAND);
        icon.setOnMouseClicked(event -> {
            password.setRevealPassword(!password.getRevealPassword());
            icon.setIconCode(password.getRevealPassword() ? Feather.EYE : Feather.EYE_OFF);
        });
        return icon;
    }

    private static VBox field(String label, javafx.scene.Node input) {
        Label caption = new Label(label);
        caption.getStyleClass().add(Styles.TEXT_SMALL);
        return new VBox(4, caption, input);
    }
}
