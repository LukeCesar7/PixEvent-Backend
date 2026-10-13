package com.pixevent.integration;

import com.pixevent.entity.Pedido;
import com.pixevent.entity.StatusMesa;
import com.pixevent.entity.StatusPedido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo de ponta a ponta: cliente cria pedido → admin confirma → portaria valida o QR Code. */
class FluxoPedidoIT extends AbstractIntegrationTest {

    @Test
    void mesas_listaTodasDisponiveisEmOrdem() throws Exception {
        mvc.perform(get("/api/mesas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].numero").value("1"))
                .andExpect(jsonPath("$[4].numero").value("5"))
                .andExpect(jsonPath("$[0].status").value("disponivel"));
    }

    @Test
    void fluxoCompleto_ingresso() throws Exception {
        // 1. cliente registra o pedido (público, sem token)
        String resp = mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Maria da Silva", "[{\"produto\":\"ingresso\",\"quantidade\":2}]", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("FN-\\d{6}")))
                .andExpect(jsonPath("$.valorTotal").value(60.0))
                .andExpect(jsonPath("$.mesas", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(resp, "$.id");

        // 2. status público: aguardando PIX
        mvc.perform(get("/api/pedido/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aguardando_pix"))
                .andExpect(jsonPath("$.pago_em").value(""));

        // 3. confirmar exige admin
        mvc.perform(post("/api/admin/pedidos/" + id + "/confirmar")).andExpect(status().isUnauthorized());

        // 4. admin confirma e recebe o QR Code
        String admin = login();
        String tokenQr = confirmar(id, admin);
        assertThat(tokenQr).matches("[0-9a-f]{40}");
        mvc.perform(get("/api/pedido/" + id + "/status"))
                .andExpect(jsonPath("$.status").value("pago"))
                .andExpect(jsonPath("$.pago_em").isNotEmpty());

        // 5. confirmar de novo = 409
        enviar(post("/api/admin/pedidos/" + id + "/confirmar"), admin, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Pedido já confirmado."));

        // 6. portaria: scanner exige token
        mvc.perform(post("/api/scanner/validar").contentType("application/json")
                        .content("{\"token\":\"" + tokenQr + "\"}"))
                .andExpect(status().isUnauthorized());

        // 7. primeira leitura é válida
        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"" + tokenQr + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.cpf").value(CPF))
                .andExpect(jsonPath("$.produto").value("ingresso"));

        // 8. segunda leitura é recusada
        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"" + tokenQr + "\"}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("QR Code já utilizado."))
                .andExpect(jsonPath("$.nome").value("Maria da Silva"));

        // 9. aparece na lista de entradas
        enviar(get("/api/admin/entradas"), admin, null)
                .andExpect(jsonPath("$.entradas", hasSize(1)))
                .andExpect(jsonPath("$.entradas[0].id").value(id));
    }

    @Test
    void scanner_tokenInexistenteOuAusente() throws Exception {
        String admin = login();
        String id = criarPedido("Joao", UM_INGRESSO, null);

        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"naoexiste\"}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("QR Code não encontrado."));
        enviar(post("/api/scanner/validar"), admin, "{}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.erro").value("Token não informado."));
        assertThat(pedidoRepository.findById(id)).isPresent();
    }

    @Test
    void pedidoComMesa_reservaConfirmaECancelaLiberando() throws Exception {
        String admin = login();
        String resp = mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Ana", "[{\"produto\":\"ingresso\",\"quantidade\":2},{\"produto\":\"mesa\",\"quantidade\":1}]", "\"2\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(160.0))
                .andExpect(jsonPath("$.mesas[0]").value("2"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(resp, "$.id");

        // mapa de mesas reflete a reserva
        mvc.perform(get("/api/mesas"))
                .andExpect(jsonPath("$[1].numero").value("2"))
                .andExpect(jsonPath("$[1].status").value("reservada"))
                .andExpect(jsonPath("$[0].status").value("disponivel"));

        // outro cliente tenta a mesma mesa: 409 e nenhum pedido novo fica gravado (rollback)
        long antes = pedidoRepository.count();
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Bia", "[{\"produto\":\"mesa\",\"quantidade\":1}]", "\"2\"")))
                .andExpect(status().isConflict());
        assertThat(pedidoRepository.count()).isEqualTo(antes);

        // confirma, depois cancela: mesa volta a ficar disponível e o QR Code deixa de valer
        String tokenQr = confirmar(id, admin);
        enviar(post("/api/admin/pedidos/" + id + "/cancelar"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesas_liberadas").value(1))
                .andExpect(jsonPath("$.ja_cancelado").value(false));

        mvc.perform(get("/api/mesas")).andExpect(jsonPath("$[1].status").value("disponivel"));
        assertThat(mesaRepository.findById("2").orElseThrow().getStatus()).isEqualTo(StatusMesa.DISPONIVEL);
        enviar(post("/api/scanner/validar"), admin, "{\"token\":\"" + tokenQr + "\"}")
                .andExpect(jsonPath("$.valido").value(false));

        // cancelar de novo é idempotente
        enviar(post("/api/admin/pedidos/" + id + "/cancelar"), admin, null)
                .andExpect(jsonPath("$.ja_cancelado").value(true))
                .andExpect(jsonPath("$.mesas_liberadas").value(0));

        // pedido cancelado não pode ser confirmado
        enviar(post("/api/admin/pedidos/" + id + "/confirmar"), admin, null).andExpect(status().isConflict());
    }

    @Test
    void semMesaEscolhida_sistemaPegaAsPrimeirasDisponiveis() throws Exception {
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Carlos", "[{\"produto\":\"mesa\",\"quantidade\":2}]", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorTotal").value(200.0))
                .andExpect(jsonPath("$.mesas[0]").value("1"))
                .andExpect(jsonPath("$.mesas[1]").value("2"));
    }

    @Test
    void mesasPodemVirComoTextoCsv() throws Exception {
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Carlos", "[{\"produto\":\"mesa\",\"quantidade\":2}]", "\"4,5\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesas[0]").value("4"))
                .andExpect(jsonPath("$.mesas[1]").value("5"));
    }

    @Test
    void maisDeTresMesas_400() throws Exception {
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Carlos", "[{\"produto\":\"mesa\",\"quantidade\":4}]", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Máximo de 3 mesas por pedido."));
        assertThat(pedidoRepository.count()).isZero();
    }

    @Test
    void faltamMesasDisponiveis_400() throws Exception {
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("Carlos", "[{\"produto\":\"mesa\",\"quantidade\":3}]", "\"1,2\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Selecione exatamente 3 mesa(s)."));
    }

    @Test
    void validacoesDeEntrada() throws Exception {
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content("{\"nome\":\"\",\"cpf\":\"" + CPF + "\",\"itens\":" + UM_INGRESSO + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Nome é obrigatório."));
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content("{\"nome\":\"X\",\"cpf\":\"123\",\"itens\":" + UM_INGRESSO + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("CPF inválido."));
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("X", "[{\"produto\":\"pizza\",\"quantidade\":1}]", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Produto inválido: pizza"));
        mvc.perform(post("/api/pedido").contentType("application/json")
                        .content(pedidoJson("X", "[{\"produto\":\"ingresso\",\"quantidade\":401}]", null)))
                .andExpect(status().isBadRequest());
        assertThat(pedidoRepository.count()).isZero();
    }

    @Test
    void jsonMalformado_400_e_metodoNaoSuportado_405() throws Exception {
        mvc.perform(post("/api/pedido").contentType("application/json").content("{nome:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida."));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/mesas"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void statusDePedidoInexistente_404() throws Exception {
        mvc.perform(get("/api/pedido/FN-000000/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Pedido não encontrado."));
    }

    @Test
    void entidadeGravadaComValoresEsperados() throws Exception {
        String id = criarPedido("Maria", "[{\"produto\":\"ingresso\",\"quantidade\":3}]", null);

        Pedido p = pedidoRepository.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PIX);
        assertThat(p.getValorTotal()).isEqualByComparingTo("90.00");
        assertThat(p.getQuantidade()).isEqualTo(3);
        assertThat(p.getQrcodeUsado()).isFalse();
        assertThat(p.getCriadoEm()).isNotNull();
        assertThat(p.getTelefone()).isEqualTo("85999998888");
    }

    @Test
    void buscarPorNome_exigeAdmin() throws Exception {
        criarPedido("Maria Souza", UM_INGRESSO, null);
        mvc.perform(get("/api/pedido/buscar").param("nome", "maria")).andExpect(status().isUnauthorized());

        enviar(get("/api/pedido/buscar").param("nome", "MARIA"), login(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Maria Souza"));
    }

    @Test
    void confirmarPeloEndpointPublicoDePedido_exigeAdmin() throws Exception {
        String id = criarPedido("Maria", UM_INGRESSO, null);
        mvc.perform(post("/api/pedido/" + id + "/confirmar")).andExpect(status().isUnauthorized());
        enviar(post("/api/pedido/" + id + "/confirmar"), login(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrcodeBase64").value(startsWith("data:image/png;base64,")));
    }
}
