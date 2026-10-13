package com.pixevent.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contexto próprio, com limites baixos, para exercitar o 429 de ponta a ponta. */
@SpringBootTest(properties = {
        "app.rate-limit.login=3",
        "app.rate-limit.api=100000",
        "spring.datasource.url=jdbc:h2:mem:pixevent_ratelimit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIT {

    @Autowired MockMvc mvc;

    @Test
    void login_bloqueiaAposOLimiteDeTentativasPorIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/admin/login").contentType("application/json").content("{\"senha\":\"errada\"}")
                            .with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/admin/login").contentType("application/json").content("{\"senha\":\"senha-teste\"}")
                        .with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("Muitas tentativas de login. Aguarde alguns minutos."));

        // outro IP não é afetado
        mvc.perform(post("/api/admin/login").contentType("application/json").content("{\"senha\":\"senha-teste\"}")
                        .with(r -> { r.setRemoteAddr("10.0.0.2"); return r; }))
                .andExpect(status().isOk());
    }

    @Test
    void documentacaoSwaggerNaoEntraNoRateLimitDaApi() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }
    }
}
