package com.pixevent.controller;

import com.pixevent.entity.Pedido;
import com.pixevent.entity.Produto;
import com.pixevent.entity.Rifa;
import com.pixevent.entity.StatusPedido;
import com.pixevent.exception.GlobalExceptionHandler;
import com.pixevent.repository.PedidoRepository;
import com.pixevent.service.RifaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScannerControllerTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock RifaService rifaService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ScannerController(pedidoRepository, rifaService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private ResultActions validar(String json) throws Exception {
        return mvc.perform(post("/api/scanner/validar").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Pedido pedido(StatusPedido status, boolean usado) {
        return Pedido.builder().id("FN-1").nome("Maria").cpf("12345678901").produto(Produto.INGRESSO)
                .mesaNumero("3").itensJson("[{\"produto\":\"ingresso\",\"quantidade\":1}]")
                .status(status).qrcodeUsado(usado).qrcodeToken("tok").pagoEm(OffsetDateTime.now()).build();
    }

    @Test
    void tokenAusenteOuVazio() throws Exception {
        validar("{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.erro").value("Token não informado."));
        validar("{\"token\":\"  \"}").andExpect(jsonPath("$.valido").value(false));
        verify(pedidoRepository, never()).findByQrcodeToken(anyString());
    }

    @Test
    void tokenNaoEncontrado() throws Exception {
        when(pedidoRepository.findByQrcodeToken("zzz")).thenReturn(Optional.empty());

        validar("{\"token\":\"zzz\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("QR Code não encontrado."));
    }

    @Test
    void pedidoNaoPago() throws Exception {
        when(pedidoRepository.findByQrcodeToken("tok")).thenReturn(Optional.of(pedido(StatusPedido.AGUARDANDO_PIX, false)));

        validar("{\"token\":\"tok\"}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("Pagamento com status: aguardando_pix."));
        verify(pedidoRepository, never()).marcarQrcodeUsadoNativo(anyString());
    }

    @Test
    void qrJaUtilizado() throws Exception {
        when(pedidoRepository.findByQrcodeToken("tok")).thenReturn(Optional.of(pedido(StatusPedido.PAGO, true)));

        validar("{\"token\":\"tok\"}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("QR Code já utilizado."))
                .andExpect(jsonPath("$.nome").value("Maria"));
        verify(pedidoRepository, never()).marcarQrcodeUsadoNativo(anyString());
    }

    @Test
    void corridaEntreDoisScanners_updateAfetaZeroLinhas() throws Exception {
        when(pedidoRepository.findByQrcodeToken("tok")).thenReturn(Optional.of(pedido(StatusPedido.PAGO, false)));
        when(pedidoRepository.marcarQrcodeUsadoNativo("tok")).thenReturn(0);

        validar("{\"token\":\"tok\"}")
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("QR Code já utilizado."));
        verify(rifaService, never()).findByPedido(anyString());
    }

    @Test
    void qrValido_marcaComoUsadoERetornaDadosDoConvidado() throws Exception {
        when(pedidoRepository.findByQrcodeToken("tok")).thenReturn(Optional.of(pedido(StatusPedido.PAGO, false)));
        when(pedidoRepository.marcarQrcodeUsadoNativo("tok")).thenReturn(1);
        when(rifaService.findByPedido("FN-1")).thenReturn(List.of(
                Rifa.builder().numero(10).build(), Rifa.builder().numero(11).build()));

        validar("{\"token\":\"tok\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.nome").value("Maria"))
                .andExpect(jsonPath("$.cpf").value("12345678901"))
                .andExpect(jsonPath("$.produto").value("ingresso"))
                .andExpect(jsonPath("$.mesa").value("3"))
                .andExpect(jsonPath("$.rifas[0]").value(10))
                .andExpect(jsonPath("$.rifas[1]").value(11));
        verify(pedidoRepository).marcarQrcodeUsadoNativo("tok");
    }

    @Test
    void tokenEhSanitizado() throws Exception {
        when(pedidoRepository.findByQrcodeToken("tok")).thenReturn(Optional.empty());

        validar("{\"token\":\" <tok> \"}").andExpect(jsonPath("$.valido").value(false));
        verify(pedidoRepository).findByQrcodeToken("tok");
    }
}
