package com.genosha.vitral.client.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.api.Models.User;
import com.genosha.vitral.client.api.VitralApi;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Function;

/** The server's accounts, and a form to create one for each person in the family. */
final class UsersPane extends VBox {

    private static final int MIN_PASSWORD = 8;

    private final VitralApi api;
    private final Consumer<Throwable> onError;
    private final Runnable onUsersChanged;
    private final ZoneId zone = ZoneId.systemDefault();

    private final TableView<User> table = new TableView<>();
    private final TextField name = new TextField();
    private final TextField email = new TextField();
    private final PasswordTextField password = new PasswordTextField();
    private final CheckBox admin = new CheckBox("Administrador: pode criar contas e cadastrar pastas");
    private final Button create = new Button("Criar conta", new FontIcon(Feather.USER_PLUS));
    private final VBox messages = new VBox();

    /** @param onUsersChanged called after an account is created, so other screens can refresh their lists */
    UsersPane(VitralApi api, Consumer<Throwable> onError, Runnable onUsersChanged) {
        this.api = api;
        this.onError = onError;
        this.onUsersChanged = onUsersChanged;

        table.getColumns().add(column("Nome", User::displayName));
        table.getColumns().add(column("E-mail", User::email));
        table.getColumns().add(column("Papel", user -> user.isAdmin() ? "Administrador" : "Membro"));
        table.getColumns().add(column("Criada em",
                user -> user.createdAt() == null ? "" : Formats.dateTime(user.createdAt(), zone)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Carregando…"));
        table.getStyleClass().add(Styles.STRIPED);
        VBox.setVgrow(table, Priority.ALWAYS);

        setSpacing(16);
        setPadding(new Insets(20));
        getChildren().addAll(table, form());
        reload();
    }

    private VBox form() {
        Label heading = new Label("Nova conta");
        heading.getStyleClass().add(Styles.TITLE_4);
        name.setPromptText("Maria");
        email.setPromptText("maria@exemplo.com");
        password.setPromptText("Pelo menos " + MIN_PASSWORD + " caracteres");
        create.getStyleClass().add(Styles.ACCENT);
        create.setOnAction(event -> createUser());

        GridPane fields = new GridPane(12, 8);
        fields.addRow(0, caption("Nome"), caption("E-mail"), caption("Senha"));
        fields.addRow(1, name, email, password);
        for (Node field : new Node[]{name, email, password}) {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
        HBox actions = new HBox(16, admin, create);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(10, heading, fields, actions, messages);
        form.getStyleClass().add("admin-form");
        form.setPadding(new Insets(16));
        return form;
    }

    private void reload() {
        Background.run(api::listUsers, users -> {
            table.getItems().setAll(users);
            table.setPlaceholder(new Label("Nenhuma conta."));
        }, error -> {
            table.setPlaceholder(new Label(ErrorMessages.describe(error)));
            onError.accept(error);
        });
    }

    private void createUser() {
        String nameValue = name.getText().strip();
        String emailValue = email.getText().strip();
        String passwordValue = password.getPassword();
        if (nameValue.isEmpty() || emailValue.isEmpty()) {
            show("Preencha o nome e o e-mail.", false);
            return;
        }
        if (passwordValue.length() < MIN_PASSWORD) {
            show("A senha precisa ter pelo menos " + MIN_PASSWORD + " caracteres.", false);
            return;
        }
        create.setDisable(true);
        boolean asAdmin = admin.isSelected();
        Background.run(() -> api.createUser(emailValue, nameValue, passwordValue, asAdmin), user -> {
            create.setDisable(false);
            name.clear();
            email.clear();
            password.setText("");
            admin.setSelected(false);
            show("Conta de " + user.displayName() + " criada. Passe o e-mail e a senha para a pessoa poder entrar.",
                    true);
            reload();
            onUsersChanged.run();
        }, error -> {
            create.setDisable(false);
            show(ErrorMessages.describe(error), false);
            onError.accept(error);
        });
    }

    private void show(String text, boolean success) {
        Message message = new Message(null, text, new FontIcon(success ? Feather.CHECK_CIRCLE : Feather.ALERT_CIRCLE));
        message.getStyleClass().add(success ? Styles.SUCCESS : Styles.DANGER);
        messages.getChildren().setAll(message);
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_SMALL);
        return label;
    }

    private static TableColumn<User, String> column(String title, Function<User, String> value) {
        TableColumn<User, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }
}
