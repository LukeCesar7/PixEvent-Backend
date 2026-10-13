package com.pixevent.integration;

import com.pixevent.entity.StatusMesa;
import com.pixevent.entity.StatusPedido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiIT extends AbstractIntegrationTest {

    // ───── autenticação ─────

    @Test
    void login_senhaCorreta_retornaToken() throws Exception {
        mvc.perform(post("/api/admin/login").contentType("application/json").content("{\"senha\":\"senha-teste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expires_in").value("1h"));
    }

    @Test
    void login_senhaErrada_401() throws Exception {
        mvc.perform(post("/api/admin/login").contentType("application/json").content("{\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Senha incorreta."));
    }

    @Test
    void rotasAdmin_semTokenOuComTokenInvalido_401() throws Exception {
        String[] rotas = {"/api/admin/metricas", "/api/admin/pedidos", "/api/admin/pedidos/pagos",
                "/api/admin/pedidos/FN-1", "/api/admin/entradas"};
        for (String rota : rotas) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value("Não autorizado ou sessão expirada."));
            enviar(get(rota), "token.invalido", null).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void segredoLegadoNaoFuncionaPorPadrao() throws Exception {
        mvc.perform(get("/api/admin/metricas").header("x-admin-secret", "segredo-legado"))
                .andExpect(status().isUnauthorized());
    }

    // ───── consulta ─────

    @Test
    void metricas_agrupaPorStatusEMostraMesas() throws Exception {
        String admin = login();
        criarPedido("A", "[{\"produto\":\"ingresso\",\"quantidade\":1}]", null);          // 30
        String pago = criarPedido("B", "[{\"produto\":\"ingresso\",\"quantidade\":2}]", null); // 60
        confirmar(pago, admin);

        enviar(get("/api/admin/metricas"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats", hasSize(2)))
                .andExpect(jsonPath("$.stats[?(@.status=='pago')].total").value(1))
                .andExpect(jsonPath("$.stats[?(@.status=='pago')].receita").value(60.0))
                .andExpect(jsonPath("$.stats[?(@.status=='aguardando_pix')].receita").value(30.0))
                .andExpect(jsonPath("$.mesas", hasSize(5)))
                .andExpect(jsonPath("$.total_rifas").value(0));
    }

    @Test
    void listarPedidos_filtrosEPaginacao() throws Exception {
        String admin = login();
        criarPedido("Maria Souza", UM_INGRESSO, null);
        String joao = criarPedido("Joao Lima", UM_INGRESSO, null);
        confirmar(joao, admin);

        enviar(get("/api/admin/pedidos"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos", hasSize(2)))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.paginas").value(1))
                .andExpect(jsonPath("$.pagina").value(1));
        enviar(get("/api/admin/pedidos").param("status", "pago"), admin, null)
                .andExpect(jsonPath("$.pedidos", hasSize(1)))
                .andExpect(jsonPath("$.pedidos[0].nome").value("Joao Lima"));
        enviar(get("/api/admin/pedidos").param("nome", "maria"), admin, null)
                .andExpect(jsonPath("$.pedidos", hasSize(1)))
                .andExpect(jsonPath("$.pedidos[0].nome").value("Maria Souza"));
        enviar(get("/api/admin/pedidos").param("status", "pago").param("nome", "maria"), admin, null)
                .andExpect(jsonPath("$.pedidos", hasSize(0)));
        enviar(get("/api/admin/pedidos").param("page", "2"), admin, null)
                .andExpect(jsonPath("$.pedidos", hasSize(0)))
                .andExpect(jsonPath("$.pagina").value(2));
        enviar(get("/api/admin/pedidos").param("status", "xyz"), admin, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidosPagos() throws Exception {
        String admin = login();
        criarPedido("A", UM_INGRESSO, null);
        confirmar(criarPedido("B", UM_INGRESSO, null), admin);

        enviar(get("/api/admin/pedidos/pagos"), admin, null)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pedidos[0].nome").value("B"));
    }

    @Test
    void buscarPorId_e_404() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", UM_INGRESSO, null);

        enviar(get("/api/admin/pedidos/" + id), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido.id").value(id))
                .andExpect(jsonPath("$.pedido.status").value("aguardando_pix"))
                .andExpect(jsonPath("$.pedido.produto").value("ingresso"));
        enviar(get("/api/admin/pedidos/FN-000000"), admin, null)
                .andExpect(status().isNotFound());
    }

    // ───── ingresso ─────

    @Test
    void ingresso_soDepoisDeConfirmado() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", UM_INGRESSO, null);

        enviar(get("/api/admin/pedidos/" + id + "/ingresso"), admin, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Pedido ainda não confirmado."));

        confirmar(id, admin);
        enviar(get("/api/admin/pedidos/" + id + "/ingresso"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrcode_base64").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.pedido.id").value(id));
    }

    @Test
    void regenerar_invalidaOTokenAnteriorEPermiteNovaEntrada() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", UM_INGRESSO, null);
        String antigo = confirmar(id, admin);

        // usa o QR Code antigo
        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"" + antigo + "\"}")
                .andExpect(jsonPath("$.valido").value(true));

        enviar(post("/api/admin/pedidos/" + id + "/ingresso/regenerar"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrcode_base64").value(startsWith("data:image/png;base64,")));

        String novo = pedidoRepository.findById(id).orElseThrow().getQrcodeToken();
        assertThat(novo).matches("[0-9a-f]{40}").isNotEqualTo(antigo);

        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"" + antigo + "\"}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("QR Code não encontrado."));
        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"" + novo + "\"}")
                .andExpect(jsonPath("$.valido").value(true));
    }

    @Test
    void regenerar_pedidoNaoConfirmado_409() throws Exception {
        String id = criarPedido("Maria", UM_INGRESSO, null);
        enviar(post("/api/admin/pedidos/" + id + "/ingresso/regenerar"), login(), null)
                .andExpect(status().isConflict());
    }

    // ───── inserir / editar / excluir ─────

    @Test
    void inserirPedidoManualmente() throws Exception {
        enviar(post("/api/admin/pedidos"), login(), pedidoJson("Balcao", "[{\"produto\":\"ingresso\",\"quantidade\":3}]", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.id").value(startsWith("FN-")))
                .andExpect(jsonPath("$.valorTotal").value(90.0));
        assertThat(pedidoRepository.count()).isEqualTo(1);
    }

    @Test
    void inserirPedidoManualmente_camposObrigatorios_400() throws Exception {
        enviar(post("/api/admin/pedidos"), login(), "{\"nome\":\"X\",\"cpf\":\"" + CPF + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("nome, cpf e itens são obrigatórios."));
    }

    @Test
    void editar_dadosCadastraisEItens() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", UM_INGRESSO, null);

        enviar(put("/api/admin/pedidos/" + id), admin,
                "{\"nome\":\"Maria Editada\",\"telefone\":\"(85) 3333-4444\","
                        + "\"itens\":[{\"produto\":\"ingresso\",\"quantidade\":4}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.pedido.nome").value("Maria Editada"));

        var p = pedidoRepository.findById(id).orElseThrow();
        assertThat(p.getNome()).isEqualTo("Maria Editada");
        assertThat(p.getTelefone()).isEqualTo("8533334444");
        assertThat(p.getQuantidade()).isEqualTo(4);
        assertThat(p.getValorTotal()).isEqualByComparingTo("120.00");
    }

    @Test
    void editar_incluindoMesa_reservaERecalcula_e_removendoLibera() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", UM_INGRESSO, null);

        enviar(put("/api/admin/pedidos/" + id), admin,
                "{\"itens\":[{\"produto\":\"mesa\",\"quantidade\":1}],\"mesa_numero\":\"3\"}")
                .andExpect(status().isOk());
        assertThat(mesaRepository.findById("3").orElseThrow().getStatus()).isEqualTo(StatusMesa.RESERVADA);
        assertThat(pedidoRepository.findById(id).orElseThrow().getValorTotal()).isEqualByComparingTo("100.00");
        assertThat(pedidoRepository.findById(id).orElseThrow().getMesaNumero()).isEqualTo("3");

        enviar(put("/api/admin/pedidos/" + id), admin, "{\"itens\":[{\"produto\":\"ingresso\",\"quantidade\":1}]}")
                .andExpect(status().isOk());
        assertThat(mesaRepository.findById("3").orElseThrow().getStatus()).isEqualTo(StatusMesa.DISPONIVEL);
        assertThat(pedidoRepository.findById(id).orElseThrow().getMesaNumero()).isNull();
    }

    @Test
    void editar_inexistente_404_e_produtoInvalido_400() throws Exception {
        String admin = login();
        enviar(put("/api/admin/pedidos/FN-000000"), admin, "{\"nome\":\"X\"}").andExpect(status().isNotFound());

        String id = criarPedido("Maria", UM_INGRESSO, null);
        enviar(put("/api/admin/pedidos/" + id), admin, "{\"itens\":[{\"produto\":\"pizza\",\"quantidade\":1}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void excluir_removePedidoELiberaMesas() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", "[{\"produto\":\"mesa\",\"quantidade\":1}]", "\"1\"");
        assertThat(mesaRepository.findById("1").orElseThrow().getStatus()).isEqualTo(StatusMesa.RESERVADA);

        enviar(delete("/api/admin/pedidos/" + id), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        assertThat(pedidoRepository.findById(id)).isEmpty();
        assertThat(mesaRepository.findById("1").orElseThrow().getStatus()).isEqualTo(StatusMesa.DISPONIVEL);
        enviar(delete("/api/admin/pedidos/" + id), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void cancelarPedidoPendente_naoPassaDeCanceladoNoBanco() throws Exception {
        String admin = login();
        String id = criarPedido("Maria", UM_INGRESSO, null);

        enviar(post("/api/admin/pedidos/" + id + "/cancelar"), admin, null).andExpect(status().isOk());
        assertThat(pedidoRepository.findById(id).orElseThrow().getStatus()).isEqualTo(StatusPedido.CANCELADO);
    }
}
