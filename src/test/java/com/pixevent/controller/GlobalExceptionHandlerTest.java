package com.pixevent.controller;

import com.pixevent.exception.ApiException;
import com.pixevent.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiException_usaStatusEMensagemDaExcecao() {
        ResponseEntity<Map<String, Object>> r = handler.handleApiException(ApiException.notFound("Pedido não encontrado."));

        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody()).containsEntry("erro", "Pedido não encontrado.");
    }

    @Test
    void apiException_fabricasDeStatus() {
        assertThat(ApiException.badRequest("x").getStatus()).isEqualTo(400);
        assertThat(ApiException.unauthorized("x").getStatus()).isEqualTo(401);
        assertThat(ApiException.notFound("x").getStatus()).isEqualTo(404);
        assertThat(ApiException.conflict("x").getStatus()).isEqualTo(409);
        assertThat(new ApiException("x", 500).getStatus()).isEqualTo(500);
    }

    @Test
    void excecaoGenerica_naoVazaDetalhesInternos() {
        ResponseEntity<Map<String, Object>> r = handler.handleGeneric(new RuntimeException("senha do banco: 123"));

        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(r.getBody()).containsEntry("erro", "Erro interno do servidor.");
        assertThat(r.getBody().toString()).doesNotContain("senha do banco");
    }

    @Test
    void requisicaoInvalidaRetorna400() {
        ResponseEntity<Map<String, Object>> r = handler.handleRequisicaoInvalida(new RuntimeException("json quebrado"));

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).containsEntry("erro", "Requisição inválida.");
    }
}
