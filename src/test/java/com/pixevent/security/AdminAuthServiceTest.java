package com.pixevent.security;

import com.pixevent.exception.ApiException;
import com.pixevent.util.AuthTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthServiceTest {

    private AuthTokenUtil tokenUtil;
    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        tokenUtil = new AuthTokenUtil();
        ReflectionTestUtils.setField(tokenUtil, "jwtSecret", "segredo");
        ReflectionTestUtils.setField(tokenUtil, "defaultExpiresIn", "8h");

        service = new AdminAuthService(tokenUtil);
        ReflectionTestUtils.setField(service, "allowLegacySecret", false);
        ReflectionTestUtils.setField(service, "adminSecret", "legado");
    }

    private MockHttpServletRequest comHeader(String nome, String valor) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader(nome, valor);
        return r;
    }

    @Test
    void aceitaBearerValido() {
        String token = tokenUtil.signToken(Map.of("role", "admin"));
        assertThat(service.isAuthorized(comHeader("Authorization", "Bearer " + token))).isTrue();
    }

    @Test
    void rejeitaSemHeader() {
        assertThat(service.isAuthorized(new MockHttpServletRequest())).isFalse();
    }

    @Test
    void rejeitaBearerInvalidoOuEsquemaErrado() {
        assertThat(service.isAuthorized(comHeader("Authorization", "Bearer lixo.lixo"))).isFalse();
        String token = tokenUtil.signToken(Map.of("role", "admin"));
        assertThat(service.isAuthorized(comHeader("Authorization", "Basic " + token))).isFalse();
    }

    @Test
    void rejeitaTokenExpirado() throws InterruptedException {
        String token = tokenUtil.signToken(Map.of("role", "admin"), "0");
        Thread.sleep(10);
        assertThat(service.isAuthorized(comHeader("Authorization", "Bearer " + token))).isFalse();
    }

    @Test
    void segredoLegadoDesabilitadoPorPadrao() {
        assertThat(service.isAuthorized(comHeader("x-admin-secret", "legado"))).isFalse();
    }

    @Test
    void segredoLegadoHabilitadoAceitaSomenteValorCorreto() {
        ReflectionTestUtils.setField(service, "allowLegacySecret", true);
        assertThat(service.isAuthorized(comHeader("x-admin-secret", "legado"))).isTrue();
        assertThat(service.isAuthorized(comHeader("x-admin-secret", "errado"))).isFalse();
    }

    @Test
    void segredoLegadoEmBrancoNuncaAutoriza() {
        ReflectionTestUtils.setField(service, "allowLegacySecret", true);
        ReflectionTestUtils.setField(service, "adminSecret", "");
        assertThat(service.isAuthorized(comHeader("x-admin-secret", ""))).isFalse();
    }

    @Test
    void requireAdmin_lanca401() {
        assertThatThrownBy(() -> service.requireAdmin(new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
    }

    @Test
    void requireAdmin_passaQuandoAutorizado() {
        String token = tokenUtil.signToken(Map.of("role", "admin"));
        service.requireAdmin(comHeader("Authorization", "Bearer " + token)); // não lança
    }
}
