package com.pixevent.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Garante que a documentação OpenAPI/Swagger está no ar e descreve a API de verdade. */
class SwaggerIT extends AbstractIntegrationTest {

    @Test
    void openApiJson_estaDisponivelEPublico() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("PixEvent API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    void openApi_documentaTodasAsRotas() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/pedido']").exists())
                .andExpect(jsonPath("$.paths['/api/pedido/{id}/status']").exists())
                .andExpect(jsonPath("$.paths['/api/pedido/buscar']").exists())
                .andExpect(jsonPath("$.paths['/api/pedido/{id}/confirmar']").exists())
                .andExpect(jsonPath("$.paths['/api/mesas']").exists())
                .andExpect(jsonPath("$.paths['/api/scanner/validar']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/login']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/metricas']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos/pagos']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos/{id}/confirmar']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos/{id}/cancelar']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos/{id}/ingresso']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pedidos/{id}/ingresso/regenerar']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/entradas']").exists())
                .andExpect(jsonPath("$.paths['/webhook/mp']").exists());
    }

    @Test
    void openApi_declaraEsquemaBearerEProtegeRotasAdmin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                // rotas protegidas exigem bearerAuth...
                .andExpect(jsonPath("$.paths['/api/admin/metricas'].get.security[*].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/scanner/validar'].post.security[*].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/pedido/buscar'].get.security[*].bearerAuth").exists())
                // ...e as públicas, não
                .andExpect(jsonPath("$.paths['/api/pedido'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/mesas'].get.security").doesNotExist());
    }

    @Test
    void openApi_loginENaoExigeAutenticacao() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        java.util.Map<String, Object> operacao = com.jayway.jsonpath.JsonPath.read(json, "$.paths['/api/admin/login'].post");
        Object security = operacao.get("security");
        // o login sobrescreve o requisito do controller: ou não há "security" ou ele é uma lista vazia
        org.assertj.core.api.Assertions.assertThat(security == null
                || (security instanceof java.util.Collection<?> c && c.isEmpty())).isTrue();
    }

    @Test
    void openApi_listaAsTags() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Admin")))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Pedidos")))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Mesas")))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Scanner")));
    }

    @Test
    void openApi_descreveOsCamposDoPedido() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.PedidoRequest.properties.cpf").exists())
                .andExpect(jsonPath("$.components.schemas.PedidoRequest.properties.itens").exists())
                .andExpect(jsonPath("$.components.schemas.ItemRequest.properties.produto.example").value("ingresso"))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.senha").exists());
    }

    @Test
    void swaggerUi_respondeComHtml() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string(containsString("Swagger UI")));
    }

    @Test
    void swaggerConfig_estaDisponivel() throws Exception {
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void swaggerUiHtml_redirecionaParaAUi() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
