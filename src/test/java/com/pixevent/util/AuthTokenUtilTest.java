package com.pixevent.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenUtilTest {

    private AuthTokenUtil util;

    @BeforeEach
    void setUp() {
        util = new AuthTokenUtil();
        ReflectionTestUtils.setField(util, "jwtSecret", "segredo-de-teste");
        ReflectionTestUtils.setField(util, "defaultExpiresIn", "8h");
    }

    @Test
    void assinaEValidaToken() {
        String token = util.signToken(Map.of("role", "admin"));

        Map<String, Object> payload = util.verifyToken(token);

        assertThat(payload).isNotNull();
        assertThat(payload).containsEntry("role", "admin").containsKeys("iat", "exp");
    }

    @Test
    void tokenTemFormatoPayloadPontoAssinatura() {
        String token = util.signToken(Map.of("role", "admin"));
        assertThat(token.split("\\.")).hasSize(2);
    }

    @Test
    void expiracaoUsaUnidadeInformada() {
        long antes = System.currentTimeMillis();
        Map<String, Object> p = util.verifyToken(util.signToken(Map.of(), "2d"));
        long exp = ((Number) p.get("exp")).longValue();
        assertThat(exp - antes).isBetween(2 * 24 * 3_600_000L - 5_000, 2 * 24 * 3_600_000L + 5_000);
    }

    @Test
    void duracaoInvalidaUsaPadraoDeOitoHoras() {
        long antes = System.currentTimeMillis();
        Map<String, Object> p = util.verifyToken(util.signToken(Map.of(), "abc"));
        long exp = ((Number) p.get("exp")).longValue();
        assertThat(exp - antes).isBetween(8 * 3_600_000L - 5_000, 8 * 3_600_000L + 5_000);
    }

    @Test
    void tokenExpiradoEhRejeitado() throws InterruptedException {
        String token = util.signToken(Map.of("role", "admin"), "0");
        Thread.sleep(10);
        assertThat(util.verifyToken(token)).isNull();
    }

    @Test
    void assinaturaAdulteradaEhRejeitada() {
        String token = util.signToken(Map.of("role", "admin"));
        String[] partes = token.split("\\.");
        assertThat(util.verifyToken(partes[0] + "." + "x".repeat(partes[1].length()))).isNull();
    }

    @Test
    void payloadAdulteradoEhRejeitado() {
        String token = util.signToken(Map.of("role", "user"));
        String sig = token.split("\\.")[1];
        String outroPayload = util.signToken(Map.of("role", "admin")).split("\\.")[0];
        assertThat(util.verifyToken(outroPayload + "." + sig)).isNull();
    }

    @Test
    void tokenSemPontoOuNuloEhRejeitado() {
        assertThat(util.verifyToken(null)).isNull();
        assertThat(util.verifyToken("semponto")).isNull();
        assertThat(util.verifyToken("")).isNull();
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRejeitado() {
        AuthTokenUtil outro = new AuthTokenUtil();
        ReflectionTestUtils.setField(outro, "jwtSecret", "outro-segredo");
        ReflectionTestUtils.setField(outro, "defaultExpiresIn", "8h");

        assertThat(util.verifyToken(outro.signToken(Map.of("role", "admin")))).isNull();
    }

    @Test
    void safeEqual() {
        assertThat(util.safeEqual("abc", "abc")).isTrue();
        assertThat(util.safeEqual("abc", "abd")).isFalse();
        assertThat(util.safeEqual("abc", "abcd")).isFalse();
        assertThat(util.safeEqual(null, "")).isTrue();
        assertThat(util.safeEqual(null, "x")).isFalse();
    }
}
