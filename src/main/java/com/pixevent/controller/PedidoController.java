package com.pixevent.controller;

import com.pixevent.dto.ItemNormalizado;
import com.pixevent.dto.PedidoRequest;
import com.pixevent.dto.PedidoResponse;
import com.pixevent.dto.RegistrarPedidoResultado;
import com.pixevent.entity.Pedido;
import com.pixevent.exception.ApiException;
import com.pixevent.repository.PedidoRepository;
import com.pixevent.security.AdminAuthService;
import com.pixevent.service.PedidoService;
import com.pixevent.util.ValidationUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedido")
public class PedidoController {

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final AdminAuthService adminAuthService;

    public PedidoController(PedidoService pedidoService, PedidoRepository pedidoRepository,
                            AdminAuthService adminAuthService) {
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.adminAuthService = adminAuthService;
    }

    // POST /api/pedido — registra pedido e retorna ID para envio via WhatsApp
    @PostMapping
    public RegistrarPedidoResultado registrar(@RequestBody PedidoRequest body) {
        String nome = ValidationUtil.cleanString(body.getNome(), 120);
        String telefone = ValidationUtil.onlyDigits(body.getTelefone(), 11);
        String cpf = ValidationUtil.onlyDigits(body.getCpf(), 11);
        List<ItemNormalizado> itens = sanitizeItens(body.getItens());

        if (nome.isEmpty()) throw ApiException.badRequest("Nome é obrigatório.");
        if (cpf.length() != 11) throw ApiException.badRequest("CPF inválido.");
        if (!telefone.isEmpty() && (telefone.length() < 10 || telefone.length() > 11)) {
            throw ApiException.badRequest("Telefone inválido.");
        }

        return pedidoService.registrarPedido(nome, telefone, cpf, itens, body.getMesa_numero());
    }

    // GET /api/pedido/buscar?nome=joao — protegido (equivalente a adminAuth no Node)
    @GetMapping("/buscar")
    public List<PedidoResponse> buscar(@RequestParam("nome") String nomeParam, HttpServletRequest request) {
        adminAuthService.requireAdmin(request);
        String nome = ValidationUtil.cleanString(nomeParam, 80);
        if (nome.isEmpty()) throw ApiException.badRequest("Informe o nome.");
        return pedidoService.buscarPorNome(nome);
    }

    // GET /api/pedido/{id}/status
    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        return Map.of("status", pedido.getStatus().getValor(),
                "pago_em", pedido.getPagoEm() == null ? "" : pedido.getPagoEm());
    }

    // POST /api/pedido/{id}/confirmar — admin confirma pagamento manual
    @PostMapping("/{id}/confirmar")
    public Map<String, Object> confirmar(@PathVariable String id, HttpServletRequest request) {
        adminAuthService.requireAdmin(request);
        var resultado = pedidoService.confirmarManual(id);
        return Map.of("ok", true,
                "qrcodeBase64", resultado.getQrcodeBase64(),
                "qrcodeToken", resultado.getQrcodeToken(),
                "pedido", resultado.getPedido());
    }

    private List<ItemNormalizado> sanitizeItens(List<com.pixevent.dto.ItemRequest> itensRequest) {
        if (itensRequest == null || itensRequest.isEmpty() || itensRequest.size() > 10) {
            throw ApiException.badRequest("Itens inválidos.");
        }
        return itensRequest.stream().map(item -> {
            String produto = ValidationUtil.cleanString(item.getProduto(), 20);
            Integer quantidade = item.getQuantidade();
            if (!produto.equals("ingresso") && !produto.equals("mesa")) {
                throw ApiException.badRequest("Produto inválido: " + produto);
            }
            if (quantidade == null || quantidade < 1 || quantidade > 400) {
                throw ApiException.badRequest("Quantidade inválida para " + produto + ".");
            }
            return new ItemNormalizado(produto, quantidade);
        }).toList();
    }
}
