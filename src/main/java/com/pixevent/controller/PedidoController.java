package com.pixevent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

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
@Tag(name = "Pedidos", description = "Criação e consulta de pedidos (públicos) e operações de balcão (admin)")
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
    @Operation(summary = "Registrar pedido", description = "Calcula o valor no servidor (ingresso R$ 30, mesa R$ 100; máx. 3 mesas) e reserva as mesas. Status inicial: aguardando_pix.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido registrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "Mesa já reservada"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido")
    })
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
    @Operation(summary = "Buscar pedidos por nome", description = "Busca parcial pelo nome. Requer admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos encontrados"),
            @ApiResponse(responseCode = "400", description = "Nome não informado"),
            @ApiResponse(responseCode = "401", description = "Não autorizado")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/buscar")
    public List<PedidoResponse> buscar(@Parameter(description = "Parte do nome do cliente", example = "maria") @RequestParam("nome") String nomeParam, HttpServletRequest request) {
        adminAuthService.requireAdmin(request);
        String nome = ValidationUtil.cleanString(nomeParam, 80);
        if (nome.isEmpty()) throw ApiException.badRequest("Informe o nome.");
        return pedidoService.buscarPorNome(nome);
    }

    // GET /api/pedido/{id}/status
    @Operation(summary = "Consultar status do pedido", description = "Público: usado pelo cliente para acompanhar o pagamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atual e data do pagamento"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        return Map.of("status", pedido.getStatus().getValor(),
                "pago_em", pedido.getPagoEm() == null ? "" : pedido.getPagoEm());
    }

    // POST /api/pedido/{id}/confirmar — admin confirma pagamento manual
    @Operation(summary = "Confirmar pagamento (admin)", description = "Mesmo comportamento de POST /api/admin/pedidos/{id}/confirmar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido confirmado + QR Code"),
            @ApiResponse(responseCode = "401", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "409", description = "Já confirmado, cancelado ou mesa indisponível")
    })
    @SecurityRequirement(name = "bearerAuth")
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
