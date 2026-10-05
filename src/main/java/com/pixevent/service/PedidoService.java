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
import com.pixevent.util.QrCodeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PedidoService {

    private static final Map<String, BigDecimal> PRECOS = Map.of(
            "ingresso", new BigDecimal("30"),
            "mesa", new BigDecimal("100")
    );
    private static final int MAX_MESAS_POR_PEDIDO = 3;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PedidoRepository pedidoRepository;
    private final MesaService mesaService;
    private final PedidoMapper pedidoMapper;

    public PedidoService(PedidoRepository pedidoRepository, MesaService mesaService,
                         PedidoMapper pedidoMapper) {
        this.pedidoRepository = pedidoRepository;
        this.mesaService = mesaService;
        this.pedidoMapper = pedidoMapper;
    }

    private static String gerarId() {
        int n = 100000 + RANDOM.nextInt(900000);
        return "FN-" + n;
    }

    private static List<ItemNormalizado> normalizarItens(List<ItemNormalizado> itens) {
        if (itens == null || itens.isEmpty()) {
            throw ApiException.badRequest("Nenhum item selecionado.");
        }
        List<ItemNormalizado> normalizados = new ArrayList<>();
        for (ItemNormalizado item : itens) {
            String produto = item.getProduto() == null ? "" : item.getProduto().trim();
            int quantidade = item.getQuantidade();

            if (!PRECOS.containsKey(produto)) {
                throw ApiException.badRequest("Produto inválido: " + produto);
            }
            if (quantidade < 1) {
                throw ApiException.badRequest("Quantidade inválida para " + produto + ".");
            }
            normalizados.add(new ItemNormalizado(produto, quantidade));
        }
        return normalizados;
    }

    private static int quantidadeMesas(List<ItemNormalizado> itens) {
        return itens.stream()
                .filter(i -> i.getProduto().equals("mesa"))
                .mapToInt(ItemNormalizado::getQuantidade)
                .sum();
    }

    private static List<String> normalizarListaMesas(Object valor) {
        if (valor == null) return List.of();
        if (valor instanceof List<?> lista) {
            return lista.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String s = String.valueOf(valor);
        if (s.isBlank() || s.equals("null")) return List.of();
        return List.of(s.split(",")).stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    private String gerarIdUnico() {
        for (int i = 0; i < 10; i++) {
            String candidato = gerarId();
            if (pedidoRepository.findById(candidato).isEmpty()) return candidato;
        }
        throw new RuntimeException("Não foi possível gerar ID. Tente novamente.");
    }

    @Transactional
    public RegistrarPedidoResultado registrarPedido(String nome, String telefone, String cpf,
                                                    List<ItemNormalizado> itensBrutos, Object mesaNumero) {
        List<ItemNormalizado> itens = normalizarItens(itensBrutos);
        com.pixevent.util.ValidationUtil.validarCadastro(nome, cpf, telefone);

        int qtdMesas = quantidadeMesas(itens);
        if (qtdMesas > MAX_MESAS_POR_PEDIDO) {
            throw ApiException.badRequest("Máximo de " + MAX_MESAS_POR_PEDIDO + " mesas por pedido.");
        }

        BigDecimal valorTotal = itens.stream()
                .map(i -> PRECOS.get(i.getProduto()).multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String id = gerarIdUnico();
        List<String> mesasSelecionadas = List.of();

        if (qtdMesas > 0) {
            List<String> solicitadas = normalizarListaMesas(mesaNumero);
            mesasSelecionadas = !solicitadas.isEmpty() ? solicitadas : mesaService.findDisponiveis(qtdMesas);

            if (mesasSelecionadas.size() != qtdMesas) {
                throw ApiException.badRequest("Selecione exatamente " + qtdMesas + " mesa(s).");
            }
            Set<String> unicas = new LinkedHashSet<>(mesasSelecionadas);
            if (unicas.size() != mesasSelecionadas.size()) {
                throw ApiException.badRequest("Há mesas repetidas na seleção.");
            }
        }

        ItemNormalizado principal = itens.get(0);

        try {
            Pedido pedido = Pedido.builder()
                    .id(id)
                    .cpf(cpf)
                    .nome(nome.trim())
                    .email("")
                    .telefone(telefone != null && !telefone.isBlank() ? telefone : "")
                    .produto(Produto.fromValor(principal.getProduto()))
                    .quantidade(principal.getQuantidade())
                    .itensJson(MAPPER.writeValueAsString(itens))
                    .mesaNumero(mesasSelecionadas.isEmpty() ? null : String.join(",", mesasSelecionadas))
                    .valorTotal(valorTotal)
                    .metodoPgto("pix_whatsapp")
                    .status(StatusPedido.AGUARDANDO_PIX)
                    .build();
            pedidoRepository.save(pedido);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao registrar pedido", e);
        }

        if (qtdMesas > 0) {
            int reservadas = mesaService.reservarMuitas(mesasSelecionadas, id);
            if (reservadas != qtdMesas) {
                throw ApiException.conflict("Uma ou mais mesas já foram reservadas. Atualize e tente novamente.");
            }
        }

        return new RegistrarPedidoResultado(id, valorTotal, mesasSelecionadas);
    }

    @Transactional
    public ConfirmarResultado confirmarManual(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));

        if (pedido.getStatus() == StatusPedido.PAGO) {
            throw ApiException.conflict("Pedido já confirmado.");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw ApiException.conflict("Pedido cancelado não pode ser confirmado.");
        }

        String qrcodeToken = gerarTokenHex(20);

        List<String> mesas = normalizarListaMesas(pedido.getMesaNumero());
        if (!mesas.isEmpty()) {
            int garantidas = mesaService.garantirReservadasParaPedido(mesas, pedidoId);
            if (garantidas != mesas.size()) {
                throw ApiException.conflict("Uma ou mais mesas deste pedido não estão disponíveis.");
            }
        }

        pedido.setStatus(StatusPedido.PAGO);
        pedido.setMpPaymentId(null);
        pedido.setQrcodeToken(qrcodeToken);
        pedido.setPagoEm(OffsetDateTime.now());
        pedidoRepository.save(pedido);

        String qrcodeBase64 = QrCodeUtil.toDataUrl(qrcodeToken, 300);

        return new ConfirmarResultado(qrcodeBase64, qrcodeToken, pedidoMapper.toResponse(pedido));
    }

    @Transactional
    public CancelarResultado cancelarPedido(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return new CancelarResultado(pedidoMapper.toResponse(pedido), 0, true);
        }

        int mesasLiberadas = mesaService.liberarDoPedido(pedidoId);

        if (mesasLiberadas == 0) {
            List<String> mesasAntigas = normalizarListaMesas(pedido.getMesaNumero());
            if (!mesasAntigas.isEmpty()) {
                mesaService.liberarMuitas(mesasAntigas);
                mesasLiberadas = mesasAntigas.size();
            }
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setQrcodeToken(null);
        pedido.setQrcodeUsado(true);
        pedido.setQrcodeUsadoEm(OffsetDateTime.now());
        pedidoRepository.save(pedido);

        return new CancelarResultado(pedidoMapper.toResponse(pedido), mesasLiberadas, false);
    }

    public List<PedidoResponse> buscarPorNome(String termo) {
        return pedidoMapper.toResponseList(
                pedidoRepository.findByNomeContainingIgnoreCaseOrderByCriadoEmDesc(termo));
    }

    private static String gerarTokenHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
