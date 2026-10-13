package com.pixevent.service;

import com.pixevent.entity.Rifa;
import com.pixevent.repository.RifaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RifaServiceTest {

    @Mock RifaRepository repo;
    @InjectMocks RifaService service;

    @Test
    void criarLote_geraNumerosSequenciaisParaOPedido() {
        when(repo.proximoNumero()).thenReturn(1, 2, 3);
        when(repo.save(any(Rifa.class))).thenAnswer(i -> i.getArgument(0));

        List<Rifa> rifas = service.criarLote("FN-111111", "12345678901", "Maria", 3);

        assertThat(rifas).extracting(Rifa::getNumero).containsExactly(1, 2, 3);
        assertThat(rifas).allSatisfy(r -> {
            assertThat(r.getPedidoId()).isEqualTo("FN-111111");
            assertThat(r.getCpf()).isEqualTo("12345678901");
            assertThat(r.getNome()).isEqualTo("Maria");
        });
    }

    @Test
    void criarLote_quantidadeZeroNaoSalvaNada() {
        assertThat(service.criarLote("FN-1", "1", "x", 0)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(repo);
    }

    @Test
    void delegaConsultasAoRepositorio() {
        when(repo.count()).thenReturn(7L);
        when(repo.findByPedidoId("FN-1")).thenReturn(List.of());

        assertThat(service.contarTotal()).isEqualTo(7L);
        assertThat(service.findByPedido("FN-1")).isEmpty();
        verify(repo).findByPedidoId("FN-1");
    }
}
