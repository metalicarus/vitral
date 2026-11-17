package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.api.ApiException;
import com.genosha.vitral.client.api.Models.DeviceSession;
import com.genosha.vitral.client.api.Models.User;
import com.genosha.vitral.client.api.VitralApi;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

/**
 * The logged-in user's own account, laid over the gallery: the name shown, the password, and
 * the devices logged in, with a way to log out a lost phone.
 */
final class AccountView extends BorderPane {

    private static final int MIN_PASSWORD = 8;

    private final VitralApi api;
    private final Consumer<User> onRenamed;
    private final Runnable onSessionExpired;
    private final ZoneId zone = ZoneId.systemDefault();

    private final TextField name = new TextField();
    private final VBox profileMessages = new VBox();
    private final PasswordTextField current = new PasswordTextField();
    private final PasswordTextField fresh = new PasswordTextField();
    private final PasswordTextField confirmation = new PasswordTextField();
    private final VBox passwordMessages = new VBox();
    private final TableView<DeviceSession> devices = new TableView<>();
    private final VBox deviceMessages = new VBox();

    /**
     * @param onClose   back to the gallery
     * @param onRenamed called with the account after its name changed, so the header can follow
     */
    AccountView(Session session, Runnable onClose, Consumer<User> onRenamed, Runnable onSessionExpired) {
        this.api = session.api();
        this.onRenamed = onRenamed;
        this.onSessionExpired = onSessionExpired;

        Button back = new Button("Voltar à galeria", new FontIcon(Feather.ARROW_LEFT));
        back.getStyleClass().add(Styles.FLAT);
        back.setOnAction(event -> onClose.run());
        Label title = new Label("Minha conta", new FontIcon(Feather.USER));
        title.getStyleClass().add(Styles.TITLE_3);
        HBox header = new HBox(16, back, title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.getStyleClass().add("app-header");

        name.setText(session.user().displayName());
        VBox sections = new VBox(20, profile(session.user()), password(), devicesSection());
        sections.setPadding(new Insets(20));
        sections.setMaxWidth(820);
        HBox centered = new HBox(sections);
        centered.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(sections, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(centered);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("albums-scroll");

        setTop(header);
        setCenter(scroll);
        getStyleClass().add("admin-view");
        loadDevices();
    }

    private Node profile(User user) {
        Label email = new Label(user.email() + (user.isAdmin() ? "  ·  administrador" : ""));
        email.getStyleClass().add(Styles.TEXT_MUTED);
        Button save = new Button("Salvar nome", new FontIcon(Feather.CHECK));
        save.setOnAction(event -> saveName(save));
        name.setOnAction(event -> saveName(save));
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox row = new HBox(12, name, save);
        row.setAlignment(Pos.CENTER_LEFT);
        return section("Perfil", "Como seu nome aparece no Vitral.", email, caption("Nome"), row, profileMessages);
    }

    private Node password() {
        current.setPromptText("A senha que você usa hoje");
        fresh.setPromptText("Pelo menos " + MIN_PASSWORD + " caracteres");
        confirmation.setPromptText("A senha nova de novo");
        Button change = new Button("Trocar senha", new FontIcon(Feather.LOCK));
        change.getStyleClass().add(Styles.ACCENT);
        change.setOnAction(event -> changePassword(change));
        return section("Senha", "Ao trocar, os outros aparelhos são desconectados; este continua conectado.",
                caption("Senha atual"), current, caption("Senha nova"), fresh, caption("Confirme a senha nova"),
                confirmation, change, passwordMessages);
    }

    private Node devicesSection() {
        TableColumn<DeviceSession, String> device = new TableColumn<>("Aparelho");
        device.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                (cell.getValue().deviceName() == null ? "Sem nome" : cell.getValue().deviceName())
                        + (cell.getValue().current() ? "  (este)" : "")));
        TableColumn<DeviceSession, String> used = new TableColumn<>("Último uso");
        used.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Formats.shortDateTime(cell.getValue().lastUsedAt(), zone)));
        TableColumn<DeviceSession, String> since = new TableColumn<>("Conectado desde");
        since.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Formats.shortDateTime(cell.getValue().createdAt(), zone)));
        TableColumn<DeviceSession, DeviceSession> action = new TableColumn<>();
        action.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        action.setSortable(false);
        action.setMinWidth(130);
        action.setMaxWidth(130);
        action.setCellFactory(col -> new TableCell<>() {
            private final Button logout = new Button("Desconectar", new FontIcon(Feather.LOG_OUT));

            {
                logout.getStyleClass().addAll(Styles.FLAT, Styles.DANGER, Styles.SMALL);
            }

            @Override
            protected void updateItem(DeviceSession session, boolean empty) {
                super.updateItem(session, empty);
                // This device logs out with "Sair" instead, which also leaves the app.
                if (empty || session == null || session.current()) {
                    setGraphic(null);
                    return;
                }
                logout.setOnAction(event -> endSession(session));
                setGraphic(logout);
            }
        });
        devices.getColumns().addAll(List.of(device, used, since, action));
        devices.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        devices.setPlaceholder(new Label("Carregando…"));
        devices.setPrefHeight(220);
        return section("Aparelhos conectados",
                "Perdeu um celular ou entrou num computador que não é seu? Desconecte o aparelho aqui.",
                devices, deviceMessages);
    }

    private void saveName(Button save) {
        String value = name.getText().strip();
        if (value.isEmpty()) {
            show(profileMessages, "O nome não pode ficar vazio.", false);
            return;
        }
        save.setDisable(true);
        Background.run(() -> api.updateProfile(value), user -> {
            save.setDisable(false);
            show(profileMessages, "Nome salvo.", true);
            onRenamed.accept(user);
        }, error -> {
            save.setDisable(false);
            failed(profileMessages, error);
        });
    }

    private void changePassword(Button change) {
        String currentValue = current.getPassword();
        String freshValue = fresh.getPassword();
        if (currentValue.isEmpty()) {
            show(passwordMessages, "Digite a senha atual.", false);
            return;
        }
        if (freshValue.length() < MIN_PASSWORD) {
            show(passwordMessages, "A senha nova precisa ter pelo menos " + MIN_PASSWORD + " caracteres.", false);
            return;
        }
        if (!freshValue.equals(confirmation.getPassword())) {
            show(passwordMessages, "As duas senhas novas não são iguais.", false);
            return;
        }
        change.setDisable(true);
        Background.run(() -> {
            api.changePassword(currentValue, freshValue);
            return null;
        }, ignored -> {
            change.setDisable(false);
            current.setText("");
            fresh.setText("");
            confirmation.setText("");
            show(passwordMessages, "Senha trocada. Os outros aparelhos foram desconectados.", true);
            loadDevices();
        }, error -> {
            change.setDisable(false);
            failed(passwordMessages, error);
        });
    }

    private void loadDevices() {
        Background.run(api::listSessions, sessions -> {
            devices.getItems().setAll(sessions);
            devices.setPlaceholder(new Label("Nenhum aparelho."));
        }, error -> failed(deviceMessages, error));
    }

    private void endSession(DeviceSession session) {
        Background.run(() -> {
            api.endSession(session.id());
            return null;
        }, ignored -> {
            show(deviceMessages, (session.deviceName() == null ? "O aparelho" : session.deviceName())
                    + " foi desconectado.", true);
            loadDevices();
        }, error -> failed(deviceMessages, error));
    }

    private void failed(VBox messages, Throwable error) {
        if (error instanceof ApiException api && api.isUnauthorized()) {
            onSessionExpired.run();
            return;
        }
        show(messages, ErrorMessages.describe(error), false);
    }

    private static void show(VBox messages, String text, boolean success) {
        Message message = new Message(null, text, new FontIcon(success ? Feather.CHECK_CIRCLE : Feather.ALERT_CIRCLE));
        message.getStyleClass().add(success ? Styles.SUCCESS : Styles.DANGER);
        messages.getChildren().setAll(message);
    }

    private static Node section(String title, String description, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add(Styles.TITLE_4);
        Label hint = new Label(description);
        hint.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        hint.setWrapText(true);
        VBox box = new VBox(10, heading, hint);
        box.getChildren().addAll(content);
        box.getStyleClass().add("admin-form");
        box.setPadding(new Insets(16));
        return box;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_SMALL);
        return label;
    }
}
