package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.ApiException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

/** Turns failures into messages for the user, in Portuguese. The server's own hints are in English. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static String describe(Throwable error) {
        return switch (error) {
            case ApiException api -> describe(api);
            case ConnectException _, HttpConnectTimeoutException _, UnknownHostException _ ->
                    "Não foi possível conectar ao servidor. Confira o endereço e se o servidor está ligado.";
            case HttpTimeoutException _ -> "O servidor demorou demais para responder. Tente de novo.";
            case IllegalArgumentException _ -> "Esse endereço de servidor não é válido.";
            case IOException _ -> "A comunicação com o servidor falhou. Tente de novo.";
            default -> "Erro inesperado: " + error.getMessage();
        };
    }

    private static String describe(ApiException error) {
        return switch (error.code()) {
            case "invalid_credentials" -> "E-mail ou senha incorretos.";
            case "login_locked" -> "Muitas tentativas erradas. Espere alguns minutos e tente de novo.";
            case "not_authenticated" -> "Sua sessão expirou. Entre de novo.";
            case "setup_done" -> "Este servidor já tem um administrador. Entre com a conta dele.";
            case "email_taken" -> "Já existe uma conta com esse e-mail.";
            case "invalid_path" -> "Use o caminho completo da pasta no servidor, como /fotos/maria.";
            // The server uses this code both for a path with no folder (400) and for an unknown watched folder (404).
            case "folder_not_found" -> error.status() == 404
                    ? "Essa pasta não é mais monitorada."
                    : "Não existe uma pasta nesse caminho no servidor.";
            case "folder_not_readable" -> "O servidor não tem permissão para ler essa pasta.";
            case "folder_overlaps_data" -> "A pasta não pode ficar dentro da pasta de dados do Vitral, nem contê-la.";
            case "folder_overlaps" -> "Essa pasta já é monitorada, ou fica dentro de outra pasta monitorada, ou contém uma.";
            case "owner_not_found" -> "Não existe uma conta com esse e-mail.";
            default -> {
                if (error.isUnauthorized()) {
                    yield "Sua sessão expirou. Entre de novo.";
                }
                if (error.status() == 403) {
                    yield "Só administradores podem fazer isso.";
                }
                if (error.status() == 400) {
                    // Field validation answers in another format, without a Vitral error code.
                    yield "O servidor recusou os dados. Confira os campos: e-mail válido e senha com pelo menos"
                            + " 8 caracteres.";
                }
                yield "O servidor respondeu com um erro (" + error.code() + ").";
            }
        };
    }
}
