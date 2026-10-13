package com.pixevent.service;

import com.pixevent.entity.Mesa;
import com.pixevent.entity.Pedido;
import com.pixevent.entity.PedidoMesa;
import com.pixevent.entity.StatusMesa;
import com.pixevent.repository.MesaRepository;
import com.pixevent.repository.PedidoMesaRepository;
import com.pixevent.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MesaServiceTest {

    @Mock MesaRepository mesaRepository;
    @Mock PedidoMesaRepository pedidoMesaRepository;
    @Mock PedidoRepository pedidoRepository;
    @InjectMocks MesaService service;

    private Mesa mesa(String numero, StatusMesa status) {
        return Mesa.builder().numero(numero).status(status).build();
    }

    private PedidoMesa vinculo(String pedidoId, Mesa mesa) {
        return PedidoMesa.builder().pedido(Pedido.builder().id(pedidoId).build()).mesa(mesa).build();
    }

    @Test
    void reservar_mesaDisponivelViraReservadaECriaVinculo() {
        Mesa m = mesa("1", StatusMesa.DISPONIVEL);
        Pedido p = Pedido.builder().id("FN-1").build();
        when(mesaRepository.findById("1")).thenReturn(Optional.of(m));
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(p));

        boolean ok = service.reservar("1", "FN-1");

        assertThat(ok).isTrue();
        assertThat(m.getStatus()).isEqualTo(StatusMesa.RESERVADA);
        assertThat(m.getPedidoId()).isEqualTo("FN-1");
        assertThat(m.getReservadoEm()).isNotNull();
        ArgumentCaptor<PedidoMesa> cap = ArgumentCaptor.forClass(PedidoMesa.class);
        verify(pedidoMesaRepository).save(cap.capture());
        assertThat(cap.getValue().getMesa()).isSameAs(m);
        assertThat(cap.getValue().getPedido()).isSameAs(p);
    }

    @Test
    void reservar_mesaJaReservadaRetornaFalse() {
        when(mesaRepository.findById("1")).thenReturn(Optional.of(mesa("1", StatusMesa.RESERVADA)));

        assertThat(service.reservar("1", "FN-1")).isFalse();
        verify(mesaRepository, never()).save(any());
        verify(pedidoMesaRepository, never()).save(any());
    }

    @Test
    void reservar_mesaInexistenteRetornaFalse() {
        when(mesaRepository.findById("99")).thenReturn(Optional.empty());
        assertThat(service.reservar("99", "FN-1")).isFalse();
    }

    @Test
    void reservarMuitas_contaSomenteAsReservadas() {
        when(mesaRepository.findById("1")).thenReturn(Optional.of(mesa("1", StatusMesa.DISPONIVEL)));
        when(mesaRepository.findById("2")).thenReturn(Optional.of(mesa("2", StatusMesa.RESERVADA)));
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(Pedido.builder().id("FN-1").build()));

        assertThat(service.reservarMuitas(List.of("1", "2"), "FN-1")).isEqualTo(1);
    }

    @Test
    void liberar_voltaMesaParaDisponivelELimpaVinculo() {
        Mesa m = mesa("1", StatusMesa.RESERVADA);
        m.setPedidoId("FN-1");
        when(mesaRepository.findById("1")).thenReturn(Optional.of(m));

        service.liberar("1");

        verify(pedidoMesaRepository).deleteByMesa_Numero("1");
        assertThat(m.getStatus()).isEqualTo(StatusMesa.DISPONIVEL);
        assertThat(m.getPedidoId()).isNull();
        assertThat(m.getReservadoEm()).isNull();
        verify(mesaRepository).save(m);
    }

    @Test
    void liberar_mesaInexistenteApenasRemoveVinculo() {
        when(mesaRepository.findById("9")).thenReturn(Optional.empty());

        service.liberar("9");

        verify(pedidoMesaRepository).deleteByMesa_Numero("9");
        verify(mesaRepository, never()).save(any());
    }

    @Test
    void liberarDoPedido_retornaQuantidadeLiberada() {
        Mesa m1 = mesa("1", StatusMesa.RESERVADA);
        Mesa m2 = mesa("2", StatusMesa.RESERVADA);
        when(pedidoMesaRepository.findByPedidoId("FN-1")).thenReturn(List.of(vinculo("FN-1", m1), vinculo("FN-1", m2)));
        when(mesaRepository.findById("1")).thenReturn(Optional.of(m1));
        when(mesaRepository.findById("2")).thenReturn(Optional.of(m2));

        assertThat(service.liberarDoPedido("FN-1")).isEqualTo(2);
        assertThat(m1.getStatus()).isEqualTo(StatusMesa.DISPONIVEL);
        assertThat(m2.getStatus()).isEqualTo(StatusMesa.DISPONIVEL);
    }

    @Test
    void liberarDoPedido_semMesasRetornaZero() {
        when(pedidoMesaRepository.findByPedidoId("FN-1")).thenReturn(List.of());
        assertThat(service.liberarDoPedido("FN-1")).isZero();
    }

    @Test
    void garantirReservadas_contaJaVinculadasEReservaDisponiveis() {
        when(pedidoMesaRepository.findByPedidoIdAndMesaNumero("FN-1", "1"))
                .thenReturn(Optional.of(vinculo("FN-1", mesa("1", StatusMesa.RESERVADA))));
        when(pedidoMesaRepository.findByPedidoIdAndMesaNumero("FN-1", "2")).thenReturn(Optional.empty());
        when(pedidoMesaRepository.findByPedidoIdAndMesaNumero("FN-1", "3")).thenReturn(Optional.empty());
        when(pedidoMesaRepository.findByPedidoIdAndMesaNumero("FN-1", "4")).thenReturn(Optional.empty());
        when(mesaRepository.findById("2")).thenReturn(Optional.of(mesa("2", StatusMesa.DISPONIVEL)));
        when(mesaRepository.findById("3")).thenReturn(Optional.of(mesa("3", StatusMesa.RESERVADA))); // de outro pedido
        when(mesaRepository.findById("4")).thenReturn(Optional.empty());
        when(pedidoRepository.findById("FN-1")).thenReturn(Optional.of(Pedido.builder().id("FN-1").build()));

        int garantidas = service.garantirReservadasParaPedido(List.of("1", "2", "3", "4"), "FN-1");

        assertThat(garantidas).isEqualTo(2); // "1" (já vinculada) e "2" (reservada agora)
    }

    @Test
    void findDisponiveisEMapeamentoDosDemaisMetodos() {
        when(mesaRepository.findNumerosDisponiveis(2)).thenReturn(List.of("1", "2"));
        when(mesaRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of(mesa("1", StatusMesa.DISPONIVEL)));
        when(mesaRepository.findById("1")).thenReturn(Optional.of(mesa("1", StatusMesa.DISPONIVEL)));
        when(pedidoMesaRepository.findByPedidoId("FN-1"))
                .thenReturn(List.of(vinculo("FN-1", mesa("7", StatusMesa.RESERVADA))));

        assertThat(service.findDisponiveis(2)).containsExactly("1", "2");
        assertThat(service.findAll()).hasSize(1);
        assertThat(service.findByNumero("1")).isPresent();
        assertThat(service.getMesasDoPedido("FN-1")).containsExactly("7");
    }
}
