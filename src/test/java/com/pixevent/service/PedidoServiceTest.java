package com.pixevent.service;

import com.pixevent.dto.CancelarResultado;
import com.pixevent.dto.ConfirmarResultado;
import com.pixevent.dto.ItemNormalizado;
import com.pixevent.dto.PedidoResponse;
import com.pixevent.dto.RegistrarPedidoResultado;
import com.pixevent.entity.Pedido;
import com.pixevent.entity.Produto;
import com.pixevent.entity.StatusPedido;
import com.pixevent.exception.ApiException;
import com.pixevent.mapper.PedidoMapper;
import com.pixevent.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock MesaService mesaService;
    @Mock PedidoMapper pedidoMapper;
    @InjectMocks PedidoService service;

    private static final String CPF = "12345678901";

    private static List<ItemNormalizado> itens(Object... pares) {
        java.util.ArrayList<ItemNormalizado> l = new java.util.ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) l.add(new ItemNormalizado((String) pares[i], (Integer) pares[i + 1]));
        return l;
    }

    private static void assertApi(Throwable t, int status, String msg) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessage()).isEqualTo(msg);
        });
    }

    private Pedido pedido(String id, StatusPedido status, String mesas) {
        return Pedido.builder().id(id).nome("Maria").cpf(CPF).telefone("").produto(Produto.INGRESSO)
                .quantidade(1).valorTotal(new BigDecimal("30.00")).status(status).mesaNumero(mesas).build();
    }

    // ───────────── registrarPedido ─────────────

    @Test
    void registrar_ingressos_calculaValorNoServidorESalvaAguardandoPix() {
        RegistrarPedidoResultado r = service.registrarPedido("  Maria ", "85999998888", CPF, itens("ingresso", 2), null);

        assertThat(r.getId()).matches("FN-\\d{6}");
        assertThat(r.getValorTotal()).isEqualByComparingTo("60.00");
        assertThat(r.getMesas()).isEmpty();

        ArgumentCaptor<Pedido> cap = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(cap.capture());
        Pedido salvo = cap.getValue();
        assertThat(salvo.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PIX);
        assertThat(salvo.getNome()).isEqualTo("Maria");
        assertThat(salvo.getProduto()).isEqualTo(Produto.INGRESSO);
        assertThat(salvo.getQuantidade()).isEqualTo(2);
        assertThat(salvo.getMesaNumero()).isNull();
        assertThat(salvo.getMetodoPgto()).isEqualTo("pix_whatsapp");
        assertThat(salvo.getItensJson()).contains("\"produto\":\"ingresso\"").contains("\"quantidade\":2");
        verify(mesaService, never()).reservarMuitas(anyList(), anyString());
    }

    @Test
    void registrar_comMesaEscolhida_reservaEAplicaValor() {
        when(mesaService.reservarMuitas(anyList(), anyString())).thenReturn(1);

        RegistrarPedidoResultado r = service.registrarPedido("Maria", "", CPF,
                itens("ingresso", 2, "mesa", 1), "5");

        assertThat(r.getValorTotal()).isEqualByComparingTo("160.00");
        assertThat(r.getMesas()).containsExactly("5");
        verify(mesaService).reservarMuitas(List.of("5"), r.getId());
    }

    @Test
    void registrar_semMesaEscolhida_pegaAsPrimeirasDisponiveis() {
        when(mesaService.findDisponiveis(2)).thenReturn(List.of("1", "2"));
        when(mesaService.reservarMuitas(anyList(), anyString())).thenReturn(2);

        RegistrarPedidoResultado r = service.registrarPedido("Maria", "", CPF, itens("mesa", 2), null);

        assertThat(r.getMesas()).containsExactly("1", "2");
        ArgumentCaptor<Pedido> cap = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(cap.capture());
        assertThat(cap.getValue().getMesaNumero()).isEqualTo("1,2");
    }

    @Test
    void registrar_aceitaMesasComoListaOuCsvComEspacos() {
        when(mesaService.reservarMuitas(anyList(), anyString())).thenReturn(2);

        assertThat(service.registrarPedido("M", "", CPF, itens("mesa", 2), List.of("3", " 4 ")).getMesas())
                .containsExactly("3", "4");
        assertThat(service.registrarPedido("M", "", CPF, itens("mesa", 2), "6, 7").getMesas())
                .containsExactly("6", "7");
    }

    @Test
    void registrar_maisDeTresMesas_400() {
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("mesa", 4), null))
                .satisfies(t -> assertApi(t, 400, "Máximo de 3 mesas por pedido."));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void registrar_quantidadeDeMesasSelecionadasDiferenteDoPedido_400() {
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("mesa", 2), "1"))
                .satisfies(t -> assertApi(t, 400, "Selecione exatamente 2 mesa(s)."));
    }

    @Test
    void registrar_mesasRepetidas_400() {
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("mesa", 2), "1,1"))
                .satisfies(t -> assertApi(t, 400, "Há mesas repetidas na seleção."));
    }

    @Test
    void registrar_semMesasDisponiveis_400() {
        when(mesaService.findDisponiveis(1)).thenReturn(List.of());
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("mesa", 1), null))
                .satisfies(t -> assertApi(t, 400, "Selecione exatamente 1 mesa(s)."));
    }

    @Test
    void registrar_mesaJaReservadaPorOutro_409() {
        when(mesaService.reservarMuitas(anyList(), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("mesa", 1), "1"))
                .satisfies(t -> assertApi(t, 409, "Uma ou mais mesas já foram reservadas. Atualize e tente novamente."));
    }

    @Test
    void registrar_itensVazios_400() {
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, List.of(), null))
                .satisfies(t -> assertApi(t, 400, "Nenhum item selecionado."));
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, null, null))
                .satisfies(t -> assertApi(t, 400, "Nenhum item selecionado."));
    }

    @Test
    void registrar_produtoInvalido_400() {
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("pizza", 1), null))
                .satisfies(t -> assertApi(t, 400, "Produto inválido: pizza"));
    }

    @Test
    void registrar_quantidadeInvalida_400() {
        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("ingresso", 0), null))
                .satisfies(t -> assertApi(t, 400, "Quantidade inválida para ingresso."));
    }

    @Test
    void registrar_cadastroInvalido_400() {
        assertThatThrownBy(() -> service.registrarPedido("", "", CPF, itens("ingresso", 1), null))
                .satisfies(t -> assertApi(t, 400, "Nome é obrigatório."));
        assertThatThrownBy(() -> service.registrarPedido("M", "", "123", itens("ingresso", 1), null))
                .satisfies(t -> assertApi(t, 400, "CPF inválido."));
        assertThatThrownBy(() -> service.registrarPedido("M", "123", CPF, itens("ingresso", 1), null))
                .satisfies(t -> assertApi(t, 400, "Telefone inválido."));
    }

    @Test
    void registrar_tentaNovoIdQuandoHaColisao() {
        when(pedidoRepository.findById(anyString()))
                .thenReturn(Optional.of(pedido("FN-000000", StatusPedido.PAGO, null)))
                .thenReturn(Optional.empty());

        RegistrarPedidoResultado r = service.registrarPedido("M", "", CPF, itens("ingresso", 1), null);

        assertThat(r.getId()).startsWith("FN-");
        verify(pedidoRepository, org.mockito.Mockito.times(2)).findById(anyString());
    }

    @Test
    void registrar_falhaAoGerarIdUnico_lancaErroInterno() {
        when(pedidoRepository.findById(anyString())).thenReturn(Optional.of(pedido("FN-1", StatusPedido.PAGO, null)));

        assertThatThrownBy(() -> service.registrarPedido("M", "", CPF, itens("ingresso", 1), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Não foi possível gerar ID");
    }

    // ───────────── confirmarManual ─────────────

    @Test
    void confirmar_pedidoInexistente_404() {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmarManual("FN-1"))
                .satisfies(t -> assertApi(t, 404, "Pedido não encontrado."));
    }

    @Test
    void confirmar_jaPago_409() {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pedido("FN-1", StatusPedido.PAGO, null)));
        assertThatThrownBy(() -> service.confirmarManual("FN-1"))
                .satisfies(t -> assertApi(t, 409, "Pedido já confirmado."));
    }

    @Test
    void confirmar_cancelado_409() {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(pedido("FN-1", StatusPedido.CANCELADO, null)));
        assertThatThrownBy(() -> service.confirmarManual("FN-1"))
                .satisfies(t -> assertApi(t, 409, "Pedido cancelado não pode ser confirmado."));
    }

    @Test
    void confirmar_geraTokenQrCodeEMarcaComoPago() {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null);
        PedidoResponse resp = org.mockito.Mockito.mock(PedidoResponse.class);
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(pedidoMapper.toResponse(p)).thenReturn(resp);

        ConfirmarResultado r = service.confirmarManual("FN-1");

        assertThat(p.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(p.getPagoEm()).isNotNull();
        assertThat(p.getQrcodeToken()).matches("[0-9a-f]{40}");
        assertThat(r.getQrcodeToken()).isEqualTo(p.getQrcodeToken());
        assertThat(r.getQrcodeBase64()).startsWith("data:image/png;base64,");
        assertThat(r.getPedido()).isSameAs(resp);
        verify(pedidoRepository).save(p);
    }

    @Test
    void confirmar_garanteAsMesasDoPedido() {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "1,2");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.garantirReservadasParaPedido(List.of("1", "2"), "FN-1")).thenReturn(2);

        service.confirmarManual("FN-1");

        assertThat(p.getStatus()).isEqualTo(StatusPedido.PAGO);
    }

    @Test
    void confirmar_mesaIndisponivel_409ENaoMarcaComoPago() {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "1,2");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.garantirReservadasParaPedido(List.of("1", "2"), "FN-1")).thenReturn(1);

        assertThatThrownBy(() -> service.confirmarManual("FN-1"))
                .satisfies(t -> assertApi(t, 409, "Uma ou mais mesas deste pedido não estão disponíveis."));
        assertThat(p.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PIX);
        verify(pedidoRepository, never()).save(any());
    }

    // ───────────── cancelarPedido ─────────────

    @Test
    void cancelar_pedidoInexistente_404() {
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancelarPedido("FN-1"))
                .satisfies(t -> assertApi(t, 404, "Pedido não encontrado."));
    }

    @Test
    void cancelar_jaCancelado_ehIdempotente() {
        Pedido p = pedido("FN-1", StatusPedido.CANCELADO, null);
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));

        CancelarResultado r = service.cancelarPedido("FN-1");

        assertThat(r.jaCancelado()).isTrue();
        assertThat(r.mesasLiberadas()).isZero();
        verify(mesaService, never()).liberarDoPedido(anyString());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelar_liberaMesasInvalidaQrCodeEMarcaCancelado() {
        Pedido p = pedido("FN-1", StatusPedido.PAGO, "1,2");
        p.setQrcodeToken("abc");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.liberarDoPedido("FN-1")).thenReturn(2);

        CancelarResultado r = service.cancelarPedido("FN-1");

        assertThat(r.jaCancelado()).isFalse();
        assertThat(r.mesasLiberadas()).isEqualTo(2);
        assertThat(p.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(p.getQrcodeToken()).isNull();
        assertThat(p.getQrcodeUsado()).isTrue();
        assertThat(p.getQrcodeUsadoEm()).isNotNull();
        verify(pedidoRepository).save(p);
        verify(mesaService, never()).liberarMuitas(anyList());
    }

    @Test
    void cancelar_semVinculos_usaCampoMesaNumeroComoFallback() {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, "3,4");
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));
        when(mesaService.liberarDoPedido("FN-1")).thenReturn(0);

        CancelarResultado r = service.cancelarPedido("FN-1");

        verify(mesaService).liberarMuitas(List.of("3", "4"));
        assertThat(r.mesasLiberadas()).isEqualTo(2);
    }

    @Test
    void cancelar_semMesas_retornaZeroLiberadas() {
        Pedido p = pedido("FN-1", StatusPedido.AGUARDANDO_PIX, null);
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));

        assertThat(service.cancelarPedido("FN-1").mesasLiberadas()).isZero();
    }

    // ───────────── buscarPorNome ─────────────

    @Test
    void buscarPorNome_delegaEMapeia() {
        List<Pedido> lista = List.of(pedido("FN-1", StatusPedido.PAGO, null));
        when(pedidoRepository.findByNomeContainingIgnoreCaseOrderByCriadoEmDesc("mar")).thenReturn(lista);
        when(pedidoMapper.toResponseList(lista)).thenReturn(List.of());

        assertThat(service.buscarPorNome("mar")).isEmpty();
        verify(pedidoMapper).toResponseList(lista);
    }
}
