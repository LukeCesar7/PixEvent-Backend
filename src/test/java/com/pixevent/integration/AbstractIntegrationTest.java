package com.pixevent.integration;

import com.jayway.jsonpath.JsonPath;
import com.pixevent.entity.Mesa;
import com.pixevent.repository.MesaRepository;
import com.pixevent.repository.PedidoMesaRepository;
import com.pixevent.repository.PedidoRepository;
import com.pixevent.repository.RifaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sobe a aplicação inteira (controllers, interceptors, JPA) com H2 em memória e
 * faz requisições HTTP simuladas via MockMvc. Antes de cada teste o banco é
 * zerado e 5 mesas (1..5) são recriadas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    protected static final String CPF = "12345678901";

    @Autowired protected MockMvc mvc;
    @Autowired protected PedidoRepository pedidoRepository;
    @Autowired protected MesaRepository mesaRepository;
    @Autowired protected PedidoMesaRepository pedidoMesaRepository;
    @Autowired protected RifaRepository rifaRepository;

    @BeforeEach
    void limparBanco() {
        pedidoMesaRepository.deleteAll();
        rifaRepository.deleteAll();
        pedidoRepository.deleteAll();
        mesaRepository.deleteAll();
        for (int i = 1; i <= 5; i++) {
            mesaRepository.save(Mesa.builder().numero(String.valueOf(i)).build());
        }
    }

    // ───── helpers ─────

    protected ResultActions enviar(MockHttpServletRequestBuilder req, String token, String json) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (json != null) req.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(req);
    }

    protected String login() throws Exception {
        String resp = mvc.perform(post("/api/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"senha-teste\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.token");
    }

    protected static String pedidoJson(String nome, String itens, String mesas) {
        return "{\"nome\":\"" + nome + "\",\"cpf\":\"" + CPF + "\",\"telefone\":\"85999998888\",\"itens\":" + itens
                + (mesas == null ? "" : ",\"mesa_numero\":" + mesas) + "}";
    }

    protected static final String UM_INGRESSO = "[{\"produto\":\"ingresso\",\"quantidade\":1}]";

    /** Cria um pedido pela API pública e devolve o id. */
    protected String criarPedido(String nome, String itens, String mesas) throws Exception {
        String resp = mvc.perform(post("/api/pedido").contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoJson(nome, itens, mesas)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.id");
    }

    /** Confirma o pagamento como admin e devolve o token do QR Code. */
    protected String confirmar(String id, String adminToken) throws Exception {
        String resp = enviar(post("/api/admin/pedidos/" + id + "/confirmar"), adminToken, null)
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.qrcodeToken");
    }
}
