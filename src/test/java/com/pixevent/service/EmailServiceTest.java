package com.pixevent.service;

import com.pixevent.entity.Pedido;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class EmailServiceTest {

    @Test
    void semApiKeyNaoEnviaENaoLancaExcecao() {
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "emailFrom", "x@y.com");
        ReflectionTestUtils.setField(service, "emailFromName", "PixEvent");

        Pedido pedido = Pedido.builder().id("FN-1").nome("Maria").cpf("12345678901").build();

        assertThatCode(() -> service.enviarConfirmacao(pedido, List.of(1, 2))).doesNotThrowAnyException();
    }

    @Test
    void apiKeyNulaTambemNaoEnvia() {
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "apiKey", null);
        Pedido pedido = Pedido.builder().id("FN-1").build();

        assertThatCode(() -> service.enviarConfirmacao(pedido, null)).doesNotThrowAnyException();
    }
}
