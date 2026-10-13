package com.pixevent.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitInterceptorTest {

    private MockHttpServletRequest req(String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void permiteAteOLimiteEBloqueiaDepois() throws Exception {
        RateLimitInterceptor in = new RateLimitInterceptor(3, 60_000, "Muitas requisições");

        for (int i = 0; i < 3; i++) {
            assertThat(in.preHandle(req("1.1.1.1"), new MockHttpServletResponse(), new Object())).isTrue();
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(in.preHandle(req("1.1.1.1"), resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).isEqualTo("{\"erro\":\"Muitas requisições\"}");
    }

    @Test
    void ipsDiferentesTemContadoresIndependentes() throws Exception {
        RateLimitInterceptor in = new RateLimitInterceptor(1, 60_000, "x");

        assertThat(in.preHandle(req("1.1.1.1"), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(in.preHandle(req("1.1.1.1"), new MockHttpServletResponse(), new Object())).isFalse();
        assertThat(in.preHandle(req("2.2.2.2"), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void usaPrimeiroIpDoXForwardedFor() throws Exception {
        RateLimitInterceptor in = new RateLimitInterceptor(1, 60_000, "x");

        MockHttpServletRequest a = req("9.9.9.9");
        a.addHeader("X-Forwarded-For", "3.3.3.3, 9.9.9.9");
        MockHttpServletRequest b = req("8.8.8.8");
        b.addHeader("X-Forwarded-For", "3.3.3.3");

        assertThat(in.preHandle(a, new MockHttpServletResponse(), new Object())).isTrue();
        // mesmo cliente original (3.3.3.3), proxies diferentes -> mesmo contador
        assertThat(in.preHandle(b, new MockHttpServletResponse(), new Object())).isFalse();
    }

    @Test
    void janelaReiniciaAposExpirar() throws Exception {
        RateLimitInterceptor in = new RateLimitInterceptor(1, 50, "x");

        assertThat(in.preHandle(req("1.1.1.1"), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(in.preHandle(req("1.1.1.1"), new MockHttpServletResponse(), new Object())).isFalse();
        Thread.sleep(80);
        assertThat(in.preHandle(req("1.1.1.1"), new MockHttpServletResponse(), new Object())).isTrue();
    }
}
