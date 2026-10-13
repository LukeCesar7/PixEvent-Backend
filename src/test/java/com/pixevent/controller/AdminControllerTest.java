package com.pixevent.controller;

import com.pixevent.dto.CancelarResultado;
import com.pixevent.dto.ConfirmarResultado;
import com.pixevent.dto.ItemNormalizado;
import com.pixevent.dto.RegistrarPedidoResultado;
import com.pixevent.entity.Pedido;
import com.pixevent.entity.Produto;
import com.pixevent.entity.StatusPedido;
import com.pixevent.exception.ApiException;
import com.pixevent.exception.GlobalExceptionHandler;
import com.pixevent.mapper.MesaMapper;
import com.pixevent.mapper.PedidoMapper;
import com.pixevent.repository.MesaRepository;
import com.pixevent.repository.PedidoRepository;
import com.pixevent.service.MesaService;
import com.pixevent.service.PedidoService;
import com.pixevent.service.RifaService;
import com.pixevent.util.AuthTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminControllerTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock MesaRepository mesaRepository;
    @Mock MesaService mesaService;
    @Mock RifaService rifaService;
    @Mock PedidoService pedidoService;
    @Mock PedidoMapper pedidoMapper;
    @Mock MesaMapper mesaMapper;

    AuthTokenUtil authTokenUtil;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        authTokenUtil = new AuthTokenUtil();
        ReflectionTestUtils.setField(authTokenUtil, "jwtSecret", "segredo");
        ReflectionTestUtils.setField(authTokenUtil, "defaultExpiresIn", "8h");
        mvc = montar("senha123", "");
        when(pedidoMapper.toResponse(any())).thenAnswer(i -> TestData.resp(((Pedido) i.getArgument(0)).getId()));
        when(pedidoMapper.toResponseList(anyList())).thenAnswer(i ->
                ((List<Pedido>) i.getArgument(0)).stream().map(p -> TestData.resp(p.getId())).toList());
    }

    private MockMvc montar(String senha, String segredoLegado) {
        AdminController c = new AdminController(pedidoRepository, mesaRepository, mesaService, rifaService,
                pedidoService, authTokenUtil, pedidoMapper, mesaMapper, senha, segredoLegado, "8h");
        return MockMvcBuilders.standaloneSetup(c).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Pedido pedido(String id, StatusPedido status, String mesas) {
        return Pedido.builder().id(id).nome("Maria").cpf("12345678901").telefone("85999998888")
                .produto(Produto.INGRESSO).quantidade(1).valorTotal(new BigDecimal("30.00"))
                .status(status).mesaNumero(mesas).build();
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String body)
            throws Exception {
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ───── login ─────

    @Test
    void login_senhaCorreta_retornaTokenValido() throws Exception {
        String resp = json(post("/api/admin/login"), "{\"senha\":\"senha123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.expires_in").value("8h"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(resp, "$.token");
        assertThat(authTokenUtil.verifyToken(token)).containsEntry("role", "admin");
    }

    @Test
    void login_senhaIncorreta_401() throws Exception {
        json(post("/api/admin/login"), "{\"senha\":\"errada\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Senha incorreta."));
    }

    @Test
    void login_semSenhaNoBody_401() throws Exception {
        json(post("/api/admin/login"), "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void login_semNenhumaSenhaConfigurada_500() throws Exception {
        mvc = montar("", "");
        json(post("/api/admin/login"), "{\"senha\":\"x\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("ADMIN_PASSWORD/ADMIN_SECRET não configurado no .env."));
    }

    @Test
    void login_usaSegredoLegadoQuandoSenhaEstaVazia() throws Exception {
        mvc = montar("", "legado");
        json(post("/api/admin/login"), "{\"senha\":\"legado\"}").andExpect(status().isOk());
    }

    // ───── métricas ─────

    @Test
    void metricas_serializaStatsComoMapasSimples() throws Exception {
        com.pixevent.repository.PedidoRepository.StatusCount sc = new com.pixevent.repository.PedidoRepository.StatusCount() {
            public StatusPedido getStatus() { return StatusPedido.PAGO; }
            public Long getTotal() { return 2L; }
            public BigDecimal getReceita() { return new BigDecimal("60.00"); }
        };
        when(pedidoRepository.contarPorStatus()).thenReturn(List.of(sc));
        when(mesaRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of());
        when(mesaMapper.toAdminResponseList(anyList())).thenReturn(List.of());
        when(rifaService.contarTotal()).thenReturn(3L);

        mvc.perform(get("/api/admin/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats[0].status").value("pago"))
                .andExpect(jsonPath("$.stats[0].total").value(2))
                .andExpect(jsonPath("$.stats[0].receita").value(60.0))
                .andExpect(jsonPath("$.total_rifas").value(3))
                .andExpect(jsonPath("$.mesas").isArray());
    }

    // ───── listagem ─────

    private Page<Pedido> pagina(Pageable p, Pedido... pedidos) {
        return new PageImpl<>(List.of(pedidos), p, 120);
    }

    @Test
    void listar_semFiltros_usaFindAllEPaginaDeCinquenta() throws Exception {
        when(pedidoRepository.findAll(any(Pageable.class))).thenAnswer(i ->
                pagina(i.getArgument(0), pedido("FN-1", StatusPedido.PAGO, null)));

        mvc.perform(get("/api/admin/pedidos").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos[0].id").value("FN-1"))
                .andExpect(jsonPath("$.total").value(120))
                .andExpect(jsonPath("$.paginas").value(3))
                .andExpect(jsonPath("$.pagina").value(2));

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(pedidoRepository).findAll(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isEqualTo(1);
        assertThat(cap.getValue().getPageSize()).isEqualTo(50);
        assertThat(cap.getValue().getSort().getOrderFor("criadoEm").isDescending()).isTrue();
    }

    @Test
    void listar_paginaZeroOuNegativaViraPrimeiraPagina() throws Exception {
        when(pedidoRepository.findAll(any(Pageable.class))).thenAnswer(i -> pagina(i.getArgument(0)));

        mvc.perform(get("/api/admin/pedidos").param("page", "-5")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(pedidoRepository).findAll(cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
    }

    @Test
    void listar_filtraPorStatus() throws Exception {
        when(pedidoRepository.findByStatus(eq(StatusPedido.PAGO), any(Pageable.class)))
                .thenAnswer(i -> pagina(i.getArgument(1)));

        mvc.perform(get("/api/admin/pedidos").param("status", "pago")).andExpect(status().isOk());
        verify(pedidoRepository).findByStatus(eq(StatusPedido.PAGO), any(Pageable.class));
    }

    @Test
    void listar_filtraPorNome() throws Exception {
        when(pedidoRepository.findByNomeContainingIgnoreCase(eq("mar"), any(Pageable.class)))
                .thenAnswer(i -> pagina(i.getArgument(1)));

        mvc.perform(get("/api/admin/pedidos").param("nome", "mar")).andExpect(status().isOk());
        verify(pedidoRepository).findByNomeContainingIgnoreCase(eq("mar"), any(Pageable.class));
    }

    @Test
    void listar_filtraPorStatusENome() throws Exception {
        when(pedidoRepository.findByStatusAndNomeContainingIgnoreCase(eq(StatusPedido.CANCELADO), eq("mar"), any(Pageable.class)))
                .thenAnswer(i -> pagina(i.getArgument(2)));

        mvc.perform(get("/api/admin/pedidos").param("status", "cancelado").param("nome", "mar"))
                .andExpect(status().isOk());
    }

    @Test
    void listar_statusInvalido_400() throws Exception {
        mvc.perform(get("/api/admin/pedidos").param("status", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status inválido: xyz"));
    }

    @Test
    void pedidosPagos() throws Exception {
        when(pedidoRepository.findByStatusOrderByPagoEmDesc(StatusPedido.PAGO))
                .thenReturn(List.of(pedido("FN-1", StatusPedido.PAGO, null), pedido("FN-2", StatusPedido.PAGO, null)));

        mvc.perform(get("/api/admin/pedidos/pagos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pedidos[1].id").value("FN-2"));
    }

    @Test
    void entradas() throws Exception {
        when(pedidoRepository.findByQrcodeUsadoTrueOrderByQrcodeUsadoEmAsc())
                .thenReturn(List.of(pedido("FN-1", StatusPedido.PAGO, null)));

        mvc.perform(get("/api/admin/entradas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradas[0].id").value("FN-1"));
    }

    // ───── por id / ingresso ─────

    @Test
    void buscarPorId() throws Exception {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pedido("FN-1", StatusPedido.PAGO, null)));
        when(pedidoRepository.findById("X")).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/pedidos/FN-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido.id").value("FN-1"));
        mvc.perform(get("/api/admin/pedidos/X")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Pedido não encontrado."));
    }

    @Test
    void ingresso_404_409_200() throws Exception {
        Pedido pago = pedido("FN-1", StatusPedido.PAGO, null);
        pago.setQrcodeToken("abc123");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pago));
        when(pedidoRepository.findById("FN-2")).thenReturn(Optional.of(pedido("FN-2", StatusPedido.AGUARDANDO_PIX, null)));
        when(pedidoRepository.findById("X")).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/pedidos/X/ingresso")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/pedidos/FN-2/ingresso")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Pedido ainda não confirmado."));
        mvc.perform(get("/api/admin/pedidos/FN-1/ingresso")).andExpect(status().isOk())
                .andExpect(jsonPath("$.qrcode_base64").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
    }

    @Test
    void regenerar_geraNovoTokenEZeraUso() throws Exception {
        Pedido pago = pedido("FN-1", StatusPedido.PAGO, null);
        pago.setQrcodeToken("antigo");
        pago.setQrcodeUsado(true);
        pago.setQrcodeUsadoEm(java.time.OffsetDateTime.now());
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pago));

        mvc.perform(post("/api/admin/pedidos/FN-1/ingresso/regenerar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrcode_base64").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        assertThat(pago.getQrcodeToken()).matches("[0-9a-f]{40}").isNotEqualTo("antigo");
        assertThat(pago.getQrcodeUsado()).isFalse();
        assertThat(pago.getQrcodeUsadoEm()).isNull();
        verify(pedidoRepository).save(pago);
    }

    @Test
    void regenerar_pedidoNaoPago_409() throws Exception {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null)));

        mvc.perform(post("/api/admin/pedidos/FN-1/ingresso/regenerar")).andExpect(status().isConflict());
        verify(pedidoRepository, never()).save(any());
    }

    // ───── confirmar / cancelar ─────

    @Test
    void confirmar_delegaAoServico() throws Exception {
        when(pedidoService.confirmarManual("FN-1"))
                .thenReturn(new ConfirmarResultado("data:image/png;base64,AA", "tok", TestData.resp("FN-1")));

        mvc.perform(post("/api/admin/pedidos/FN-1/confirmar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.qrcodeToken").value("tok"));
    }

    @Test
    void cancelar_retornaCamposEmSnakeCase() throws Exception {
        when(pedidoService.cancelarPedido("FN-1")).thenReturn(new CancelarResultado(TestData.resp("FN-1"), 2, false));

        mvc.perform(post("/api/admin/pedidos/FN-1/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.mesas_liberadas").value(2))
                .andExpect(jsonPath("$.ja_cancelado").value(false))
                .andExpect(jsonPath("$.pedido.id").value("FN-1"));
    }

    // ───── inserir ─────

    @Test
    void inserir_camposObrigatorios_400() throws Exception {
        json(post("/api/admin/pedidos"), "{\"nome\":\"Maria\",\"cpf\":\"12345678901\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("nome, cpf e itens são obrigatórios."));
        json(post("/api/admin/pedidos"), "{\"nome\":\"\",\"cpf\":\"12345678901\",\"itens\":[{\"produto\":\"ingresso\",\"quantidade\":1}]}")
                .andExpect(status().isBadRequest());
        verify(pedidoService, never()).registrarPedido(any(), any(), any(), any(), any());
    }

    @Test
    void inserir_ok() throws Exception {
        when(pedidoService.registrarPedido(any(), any(), any(), any(), any()))
                .thenReturn(new RegistrarPedidoResultado("FN-9", new BigDecimal("30.00"), List.of()));

        json(post("/api/admin/pedidos"),
                "{\"nome\":\"Maria\",\"cpf\":\"123.456.789-01\",\"telefone\":\"85 99999-8888\","
                        + "\"itens\":[{\"produto\":\"ingresso\",\"quantidade\":1}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.id").value("FN-9"))
                .andExpect(jsonPath("$.valorTotal").value(30.0));

        verify(pedidoService).registrarPedido(eq("Maria"), eq("85999998888"), eq("12345678901"), anyList(), any());
    }

    // ───── editar ─────

    @Test
    void editar_pedidoInexistente_404() throws Exception {
        when(pedidoRepository.findById("X")).thenReturn(Optional.empty());
        json(put("/api/admin/pedidos/X"), "{\"nome\":\"Novo\"}").andExpect(status().isNotFound());
    }

    @Test
    void editar_soDadosCadastrais_naoMexeEmItensNemMesas() throws Exception {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null);
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));

        json(put("/api/admin/pedidos/FN-1"), "{\"nome\":\" <i>Joana</i> \",\"cpf\":\"987.654.321-00\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        assertThat(p.getNome()).isEqualTo("iJoana/i");
        assertThat(p.getCpf()).isEqualTo("98765432100");
        assertThat(p.getTelefone()).isEqualTo("85999998888"); // mantido
        assertThat(p.getValorTotal()).isEqualByComparingTo("30.00");
        verify(pedidoRepository).save(p);
        verify(mesaService, never()).reservar(anyString(), anyString());
    }

    @Test
    void editar_recalculaValorEReservaNovaMesa() throws Exception {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null);
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.reservar("5", "FN-1")).thenReturn(true);

        json(put("/api/admin/pedidos/FN-1"),
                "{\"itens\":[{\"produto\":\"mesa\",\"quantidade\":1},{\"produto\":\"ingresso\",\"quantidade\":2}],\"mesa_numero\":\"5\"}")
                .andExpect(status().isOk());

        assertThat(p.getValorTotal()).isEqualByComparingTo("160.00");
        assertThat(p.getProduto()).isEqualTo(Produto.MESA);
        assertThat(p.getQuantidade()).isEqualTo(1);
        assertThat(p.getMesaNumero()).isEqualTo("5");
        assertThat(p.getItensJson()).contains("mesa").contains("ingresso");
        verify(mesaService).reservar("5", "FN-1");
    }

    @Test
    void editar_trocaDeMesa_liberaAsRemovidasEReservaAsNovas() throws Exception {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "1,2");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.reservar("3", "FN-1")).thenReturn(true);

        json(put("/api/admin/pedidos/FN-1"),
                "{\"itens\":[{\"produto\":\"mesa\",\"quantidade\":2}],\"mesa_numero\":[\"2\",\"3\"]}")
                .andExpect(status().isOk());

        verify(mesaService).liberarMuitas(List.of("1"));
        verify(mesaService).reservar("3", "FN-1");
        verify(mesaService, never()).reservar(eq("2"), anyString());
        assertThat(p.getMesaNumero()).isEqualTo("2,3");
    }

    @Test
    void editar_removendoMesasDoPedido_liberaTodasEZeraCampo() throws Exception {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "1,2");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));

        json(put("/api/admin/pedidos/FN-1"), "{\"itens\":[{\"produto\":\"ingresso\",\"quantidade\":1}]}")
                .andExpect(status().isOk());

        verify(mesaService).liberarMuitas(List.of("1", "2"));
        assertThat(p.getMesaNumero()).isNull();
    }

    @Test
    void editar_validacoesDeItens_400() throws Exception {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null)));

        json(put("/api/admin/pedidos/FN-1"), "{\"itens\":[{\"produto\":\"pizza\",\"quantidade\":1}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Produto inválido: pizza"));
        json(put("/api/admin/pedidos/FN-1"), "{\"itens\":[{\"produto\":\"mesa\",\"quantidade\":2}],\"mesa_numero\":\"1\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Selecione exatamente 2 mesa(s)."));
        json(put("/api/admin/pedidos/FN-1"), "{\"itens\":[{\"produto\":\"mesa\",\"quantidade\":2}],\"mesa_numero\":\"1,1\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Há mesas repetidas na seleção."));
    }

    @Test
    void editar_mesaIndisponivel_500() throws Exception {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null)));
        when(mesaService.reservar("7", "FN-1")).thenReturn(false);

        json(put("/api/admin/pedidos/FN-1"), "{\"itens\":[{\"produto\":\"mesa\",\"quantidade\":1}],\"mesa_numero\":\"7\"}")
                .andExpect(status().isInternalServerError());
    }

    // ───── excluir ─────

    @Test
    void excluir_liberaMesasERemove() throws Exception {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "1");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.liberarDoPedido("FN-1")).thenReturn(1);

        mvc.perform(delete("/api/admin/pedidos/FN-1")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        verify(pedidoRepository).delete(p);
        verify(mesaService, never()).liberarMuitas(anyList());
    }

    @Test
    void excluir_semVinculos_usaMesaNumeroComoFallback() throws Exception {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "1,2");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.liberarDoPedido("FN-1")).thenReturn(0);

        mvc.perform(delete("/api/admin/pedidos/FN-1")).andExpect(status().isOk());

        verify(mesaService).liberarMuitas(List.of("1", "2"));
        verify(pedidoRepository).delete(p);
    }

    @Test
    void excluir_inexistente_404() throws Exception {
        when(pedidoRepository.findById("X")).thenReturn(Optional.empty());
        mvc.perform(delete("/api/admin/pedidos/X")).andExpect(status().isNotFound());
        verify(pedidoRepository, never()).delete(any());
    }
}
