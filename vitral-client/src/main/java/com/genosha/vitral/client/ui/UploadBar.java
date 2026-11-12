package com.genosha.vitral.client.ui;

import atlantafx.base.theme.Styles;
import com.genosha.vitral.client.ui.UploadJob.Phase;
import com.genosha.vitral.client.ui.UploadJob.Summary;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * The strip at the bottom of the gallery that follows an upload: what it is doing, how far
 * it got, and at the end a summary of what was sent, what the server already had and what failed.
 */
final class UploadBar extends HBox {

    private final FontIcon icon = new FontIcon(Feather.UPLOAD_CLOUD);
    private final Label text = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final Button cancel = new Button("Cancelar");
    private final Button details = new Button("Ver problemas");
    private final Button close = new Button(null, new FontIcon(Feather.X));

    UploadBar(Runnable onCancel) {
        text.setMaxWidth(Double.MAX_VALUE);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        progress.setPrefWidth(220);
        progress.getStyleClass().add(Styles.SMALL);

        cancel.getStyleClass().add(Styles.FLAT);
        cancel.setOnAction(event -> {
            cancel.setDisable(true);
            text.setText("Cancelando…");
            onCancel.run();
        });
        details.getStyleClass().add(Styles.FLAT);
        close.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        close.setOnAction(event -> setShown(false));

        getChildren().addAll(icon, text, progress, cancel, details, close);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setPadding(new Insets(8, 16, 8, 16));
        getStyleClass().add("upload-bar");
        setShown(false);
    }

    void showProgress(Phase phase, int done, int total, String fileName) {
        setShown(true);
        icon.setIconCode(Feather.UPLOAD_CLOUD);
        icon.getStyleClass().removeAll(Styles.SUCCESS, Styles.WARNING);
        show(progress, cancel);
        hide(details, close);
        switch (phase) {
            case COLLECTING -> {
                text.setText("Procurando fotos…");
                progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            }
            case HASHING -> {
                text.setText("Verificando " + (done + 1) + " de " + total + " · " + fileName);
                progress.setProgress(total == 0 ? 0 : (double) done / total);
            }
            case CHECKING -> {
                text.setText("Comparando com o que já está no servidor…");
                progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            }
            case UPLOADING -> {
                text.setText("Enviando " + (done + 1) + " de " + total + " · " + fileName);
                progress.setProgress(total == 0 ? 0 : (double) done / total);
            }
        }
    }

    /** @param onDetails shows the files that could not be sent; offered only when there are any */
    void showSummary(Summary summary, Runnable onDetails) {
        setShown(true);
        hide(progress, cancel);
        show(close);
        cancel.setDisable(false);

        boolean problems = !summary.failures().isEmpty();
        icon.setIconCode(summary.cancelled() ? Feather.X_CIRCLE : problems ? Feather.ALERT_TRIANGLE : Feather.CHECK_CIRCLE);
        icon.getStyleClass().removeAll(Styles.SUCCESS, Styles.WARNING);
        icon.getStyleClass().add(problems || summary.cancelled() ? Styles.WARNING : Styles.SUCCESS);
        text.setText(describe(summary));
        if (problems) {
            details.setOnAction(event -> onDetails.run());
            show(details);
        } else {
            hide(details);
        }
    }

    /** For example: {@code 3 fotos enviadas · 37 já estavam no servidor · 1 não foi enviada}. */
    static String describe(Summary summary) {
        List<String> parts = new ArrayList<>();
        if (summary.uploaded() > 0) {
            parts.add(Formats.count(summary.uploaded(), "foto enviada", "fotos enviadas"));
        }
        if (summary.alreadyThere() > 0) {
            parts.add(Formats.count(summary.alreadyThere(), "já estava no servidor", "já estavam no servidor"));
        }
        if (!summary.failures().isEmpty()) {
            parts.add(Formats.count(summary.failures().size(), "não foi enviada", "não foram enviadas"));
        }
        if (summary.ignored() > 0) {
            parts.add(Formats.count(summary.ignored(), "arquivo ignorado (não é foto)", "arquivos ignorados (não são fotos)"));
        }
        String result = parts.isEmpty() ? "Nenhuma foto encontrada no que foi escolhido." : String.join(" · ", parts);
        return summary.cancelled() ? "Envio cancelado. " + result : result;
    }

    private void setShown(boolean shown) {
        setVisible(shown);
        setManaged(shown);
    }

    private static void show(Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(true);
            node.setManaged(true);
        }
    }

    private static void hide(Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }
}
