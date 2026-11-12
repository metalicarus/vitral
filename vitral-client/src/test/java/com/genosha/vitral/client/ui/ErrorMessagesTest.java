package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.ApiException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessagesTest {

    @Test
    void knownServerCodesBecomeMessagesInPortuguese() {
        assertEquals("Já existe uma conta com esse e-mail.",
                ErrorMessages.describe(new ApiException(409, "email_taken", "A user with this e-mail already exists.")));
        assertEquals("Este servidor já tem um administrador. Entre com a conta dele.",
                ErrorMessages.describe(new ApiException(409, "setup_done", "The server is already set up.")));
    }

    @Test
    void folderNotFoundDependsOnTheStatus() {
        // The server uses the same code for a missing path and for an unknown watched folder.
        assertEquals("Não existe uma pasta nesse caminho no servidor.",
                ErrorMessages.describe(new ApiException(400, "folder_not_found", "No folder at this path.")));
        assertEquals("Essa pasta não é mais monitorada.",
                ErrorMessages.describe(new ApiException(404, "folder_not_found", "Watched folder not found.")));
    }

    @Test
    void unknownCodesFallBackOnTheStatus() {
        assertEquals("Só administradores podem fazer isso.",
                ErrorMessages.describe(new ApiException(403, "http_403", "Forbidden")));
        assertEquals("Sua sessão expirou. Entre de novo.",
                ErrorMessages.describe(new ApiException(401, "http_401", "Unauthorized")));
        assertEquals("O servidor respondeu com um erro (algo_novo).",
                ErrorMessages.describe(new ApiException(500, "algo_novo", "Something new.")));
    }

    @Test
    void connectionProblemsAreExplained() {
        assertEquals("Não foi possível conectar ao servidor. Confira o endereço e se o servidor está ligado.",
                ErrorMessages.describe(new ConnectException("Connection refused")));
    }
}
