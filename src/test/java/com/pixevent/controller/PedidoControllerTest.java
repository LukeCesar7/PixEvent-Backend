package com.pixevent.controller;

import com.pixevent.dto.ConfirmarResultado;
import com.pixevent.dto.ItemNormalizado;
import com.pixevent.dto.RegistrarPedidoResultado;
import com.pixevent.entity.Pedido;
import com.pixevent.entity.Produto;
import com.pixevent.entity.StatusPedido;
import com.pixevent.exception.ApiException;
import com.pixevent.exception.GlobalExceptionHandler;
import com.pixevent.repository.PedidoRepository;
import com.pixevent.security.AdminAuthService;
import com.pixevent.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoControllerTest {

    @Mock PedidoService pedidoService;
    @Mock PedidoRepository pedidoRepository;
    @Mock AdminAuthService adminAuthService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PedidoController(pedidoService, pedidoRepository, adminAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String nome, String cpf, String telefone, String itens) {
        return "{\"nome\":\"" + nome + "\",\"cpf\":\"" + cpf + "\",\"telefone\":\"" + telefone + "\",\"itens\":" + itens + "}";
    }

    private org.springframework.test.web.servlet.ResultActions postPedido(String json) throws Exception {
        return mvc.perform(post("/api/pedido").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ───── POST /api/pedido ─────

    @Test
    void registrar_ok_sanitizaEntradasERetornaResultado() throws Exception {
        when(pedidoService.registrarPedido(any(), any(), any(), any(), any()))
                .thenReturn(new RegistrarPedidoResultado("FN-123456", new BigDecimal("160.00"), List.of("5")));

        postPedido("{\"nome\":\" <b>Maria</b> \",\"cpf\":\"123.456.789-01\",\"telefone\":\"(85) 99999-8888\","
                + "\"itens\":[{\"produto\":\"ingresso\",\"quantidade\":2},{\"produto\":\"mesa\",\"quantidade\":1}],"
                + "\"mesa_numero\":[\"5\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("FN-123456"))
                .andExpect(jsonPath("$.valorTotal").value(160.0))
                .andExpect(jsonPath("$.mesas[0]").value("5"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemNormalizado>> itens = ArgumentCaptor.forClass(List.class);
        verify(pedidoService).registrarPedido(eq("bMaria/b"), eq("85999998888"), eq("12345678901"),
                itens.capture(), eq(List.of("5")));
        assertThat(itens.getValue()).extracting(ItemNormalizado::getProduto, ItemNormalizado::getQuantidade)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ingresso", 2),
                        org.assertj.core.groups.Tuple.tuple("mesa", 1));
    }

    @Test
    void registrar_telefoneOpcional() throws Exception {
        when(pedidoService.registrarPedido(any(), any(), any(), any(), any()))
                .thenReturn(new RegistrarPedidoResultado("FN-1", BigDecimal.TEN, List.of()));

        postPedido(body("Maria", "12345678901", "", "[{\"produto\":\"ingresso\",\"quantidade\":1}]"))
                .andExpect(status().isOk());
    }

    @Test
    void registrar_nomeVazio_400() throws Exception {
        postPedido(body("   ", "12345678901", "", "[{\"produto\":\"ingresso\",\"quantidade\":1}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Nome é obrigatório."));
        verify(pedidoService, never()).registrarPedido(any(), any(), any(), any(), any());
    }

    @Test
    void registrar_cpfInvalido_400() throws Exception {
        postPedido(body("Maria", "123", "", "[{\"produto\":\"ingresso\",\"quantidade\":1}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("CPF inválido."));
    }

    @Test
    void registrar_telefoneInvalido_400() throws Exception {
        postPedido(body("Maria", "12345678901", "123", "[{\"produto\":\"ingresso\",\"quantidade\":1}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Telefone inválido."));
    }

    @Test
    void registrar_itensAusentesOuVazios_400() throws Exception {
        postPedido("{\"nome\":\"Maria\",\"cpf\":\"12345678901\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Itens inválidos."));
        postPedido(body("Maria", "12345678901", "", "[]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Itens inválidos."));
    }

    @Test
    void registrar_maisDeDezItens_400() throws Exception {
        String item = "{\"produto\":\"ingresso\",\"quantidade\":1}";
        String onze = "[" + String.join(",", java.util.Collections.nCopies(11, item)) + "]";
        postPedido(body("Maria", "12345678901", "", onze))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Itens inválidos."));
    }

    @Test
    void registrar_produtoInvalido_400() throws Exception {
        postPedido(body("Maria", "12345678901", "", "[{\"produto\":\"pizza\",\"quantidade\":1}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Produto inválido: pizza"));
    }

    @Test
    void registrar_quantidadeForaDoIntervalo_400() throws Exception {
        for (String q : new String[]{"0", "401", "null"}) {
            postPedido(body("Maria", "12345678901", "", "[{\"produto\":\"ingresso\",\"quantidade\":" + q + "}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value("Quantidade inválida para ingresso."));
        }
    }

    @Test
    void registrar_erroDoServicoEhPropagadoComStatus() throws Exception {
        when(pedidoService.registrarPedido(any(), any(), any(), any(), any()))
                .thenThrow(ApiException.conflict("Uma ou mais mesas já foram reservadas."));

        postPedido(body("Maria", "12345678901", "", "[{\"produto\":\"mesa\",\"quantidade\":1}]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Uma ou mais mesas já foram reservadas."));
    }

    // ───── GET /api/pedido/{id}/status ─────

    @Test
    void status_pedidoExistente() throws Exception {
        Pedido p = Pedido.builder().id("FN-1").status(StatusPedido.AGUARDANDO_PIX).build();
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));

        mvc.perform(get("/api/pedido/FN-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aguardando_pix"))
                .andExpect(jsonPath("$.pago_em").value(""));
    }

    @Test
    void status_pedidoInexistente_404() throws Exception {
        when(pedidoRepository.findById("X")).thenReturn(Optional.empty());

        mvc.perform(get("/api/pedido/X/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Pedido não encontrado."));
    }

    // ───── GET /api/pedido/buscar ─────

    @Test
    void buscar_exigeAdmin() throws Exception {
        doThrow(ApiException.unauthorized("Não autorizado ou sessão expirada."))
                .when(adminAuthService).requireAdmin(any());

        mvc.perform(get("/api/pedido/buscar").param("nome", "maria"))
                .andExpect(status().isUnauthorized());
        verify(pedidoService, never()).buscarPorNome(any());
    }

    @Test
    void buscar_nomeVazio_400() throws Exception {
        mvc.perform(get("/api/pedido/buscar").param("nome", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o nome."));
    }

    @Test
    void buscar_ok() throws Exception {
        when(pedidoService.buscarPorNome("maria")).thenReturn(List.of(TestData.resp("FN-1")));

        mvc.perform(get("/api/pedido/buscar").param("nome", "maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("FN-1"));
    }

    // ───── POST /api/pedido/{id}/confirmar ─────

    @Test
    void confirmar_exigeAdmin() throws Exception {
        doThrow(ApiException.unauthorized("Não autorizado ou sessão expirada."))
                .when(adminAuthService).requireAdmin(any());

        mvc.perform(post("/api/pedido/FN-1/confirmar")).andExpect(status().isUnauthorized());
        verify(pedidoService, never()).confirmarManual(any());
    }

    @Test
    void confirmar_ok() throws Exception {
        when(pedidoService.confirmarManual("FN-1"))
                .thenReturn(new ConfirmarResultado("data:image/png;base64,AAA", "tok", TestData.resp("FN-1")));

        mvc.perform(post("/api/pedido/FN-1/confirmar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.qrcodeToken").value("tok"))
                .andExpect(jsonPath("$.qrcodeBase64").value("data:image/png;base64,AAA"))
                .andExpect(jsonPath("$.pedido.id").value("FN-1"));
    }

    @Test
    void confirmar_conflitoDoServico_409() throws Exception {
        when(pedidoService.confirmarManual("FN-1")).thenThrow(ApiException.conflict("Pedido já confirmado."));

        mvc.perform(post("/api/pedido/FN-1/confirmar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Pedido já confirmado."));
    }
}
