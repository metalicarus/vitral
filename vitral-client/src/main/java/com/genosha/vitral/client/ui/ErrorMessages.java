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
            default -> error.isUnauthorized()
                    ? "Sua sessão expirou. Entre de novo."
                    : "O servidor respondeu com um erro (" + error.code() + ").";
        };
    }
}
