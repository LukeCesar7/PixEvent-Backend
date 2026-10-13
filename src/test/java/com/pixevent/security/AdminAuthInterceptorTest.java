package com.pixevent.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthInterceptorTest {

    @Test
    void liberaQuandoAutorizado() throws Exception {
        AdminAuthService svc = mock(AdminAuthService.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        when(svc.isAuthorized(req)).thenReturn(true);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(new AdminAuthInterceptor(svc).preHandle(req, resp, new Object())).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void respondeHttp401ComJsonQuandoNaoAutorizado() throws Exception {
        AdminAuthService svc = mock(AdminAuthService.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        when(svc.isAuthorized(req)).thenReturn(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(new AdminAuthInterceptor(svc).preHandle(req, resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).contains("Não autorizado");
    }
}
