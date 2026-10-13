package com.pixevent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.pixevent.dto.*;
import com.pixevent.entity.Pedido;
import com.pixevent.entity.StatusPedido;
import com.pixevent.exception.ApiException;
import com.pixevent.mapper.MesaMapper;
import com.pixevent.mapper.PedidoMapper;
import com.pixevent.repository.MesaRepository;
import com.pixevent.repository.PedidoRepository;
import com.pixevent.service.MesaService;
import com.pixevent.service.PedidoService;
import com.pixevent.service.RifaService;
import com.pixevent.util.AuthTokenUtil;
import com.pixevent.util.QrCodeUtil;
import com.pixevent.util.ValidationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Painel administrativo (exige token Bearer, exceto o login)")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
public class AdminController {

    private static final Map<String, BigDecimal> PRECOS = Map.of(
            "ingresso", new BigDecimal("30"),
            "mesa", new BigDecimal("100")
    );
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final MesaService mesaService;
    private final RifaService rifaService;
    private final PedidoService pedidoService;
    private final AuthTokenUtil authTokenUtil;
    private final PedidoMapper pedidoMapper;
    private final MesaMapper mesaMapper;

    private final String adminPassword;
    private final String adminSecretLegado;
    private final String jwtExpiresIn;

    public AdminController(PedidoRepository pedidoRepository, MesaRepository mesaRepository,
                           MesaService mesaService, RifaService rifaService, PedidoService pedidoService,
                           AuthTokenUtil authTokenUtil, PedidoMapper pedidoMapper, MesaMapper mesaMapper,
                           @org.springframework.beans.factory.annotation.Value("${app.admin.password:}") String adminPassword,
                           @org.springframework.beans.factory.annotation.Value("${app.admin.secret:}") String adminSecretLegado,
                           @org.springframework.beans.factory.annotation.Value("${app.jwt.expires-in:8h}") String jwtExpiresIn) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.mesaService = mesaService;
        this.rifaService = rifaService;
        this.pedidoService = pedidoService;
        this.authTokenUtil = authTokenUtil;
        this.pedidoMapper = pedidoMapper;
        this.mesaMapper = mesaMapper;
        this.adminPassword = adminPassword;
        this.adminSecretLegado = adminSecretLegado;
        this.jwtExpiresIn = jwtExpiresIn;
    }

    // POST /api/admin/login — troca a senha por token temporário
    @Operation(summary = "Login do administrador", description = "Troca a senha por um token temporário (Bearer).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido"),
            @ApiResponse(responseCode = "401", description = "Senha incorreta"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas excedido"),
            @ApiResponse(responseCode = "500", description = "Senha do admin não configurada")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body) {
        String senha = body.getSenha() == null ? "" : body.getSenha();
        String senhaEsperada = !adminPassword.isBlank() ? adminPassword : adminSecretLegado;

        if (senhaEsperada.isBlank()) {
            throw new ApiException("ADMIN_PASSWORD/ADMIN_SECRET não configurado no .env.", 500);
        }
        if (!authTokenUtil.safeEqual(senha, senhaEsperada)) {
            throw ApiException.unauthorized("Senha incorreta.");
        }

        String token = authTokenUtil.signToken(Map.of("role", "admin"));
        return Map.of("ok", true, "token", token, "expires_in", jwtExpiresIn);
    }

    // GET /api/admin/metricas
    @Operation(summary = "Métricas gerais", description = "Totais por status do pedido (quantidade e receita), mapa de mesas e total de rifas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Métricas")
    })
    @GetMapping("/metricas")
    public Map<String, Object> metricas() {
        // Projeções do Spring Data são proxies; convertemos para mapas simples para a serialização JSON ser estável.
        List<Map<String, Object>> stats = pedidoRepository.contarPorStatus().stream().map(sc -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", sc.getStatus().getValor());
            m.put("total", sc.getTotal());
            m.put("receita", sc.getReceita());
            return m;
        }).toList();
        var mesas = mesaRepository.findAllByOrderByNumeroAsc();
        long totalRifas = rifaService.contarTotal();
        return Map.of("stats", stats, "mesas", mesaMapper.toAdminResponseList(mesas), "total_rifas", totalRifas);
    }

    // GET /api/admin/pedidos?status=pago&nome=luqui&page=1
    @Operation(summary = "Listar pedidos (paginado)", description = "50 por página, ordenado do mais recente ao mais antigo. Filtros opcionais por status e nome.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de pedidos"),
            @ApiResponse(responseCode = "400", description = "Status inválido")
    })
    @GetMapping("/pedidos")
    public Map<String, Object> listarPedidos(@Parameter(description = "Número da página (começa em 1)") @RequestParam(defaultValue = "1") int page,
                                             @Parameter(description = "Filtra por status: pendente, aguardando_pix, pago, cancelado, expirado") @RequestParam(required = false) String status,
                                             @Parameter(description = "Busca parcial (sem diferenciar maiúsculas) no nome") @RequestParam(required = false) String nome) {
        int limit = 50;
        String statusLimpo = ValidationUtil.cleanString(status, 30);
        String nomeLimpo = ValidationUtil.cleanString(nome, 80);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by(Sort.Direction.DESC, "criadoEm"));

        Page<Pedido> resultado;
        boolean temStatus = !statusLimpo.isBlank();
        boolean temNome = !nomeLimpo.isBlank();

        StatusPedido statusFiltro = null;
        if (temStatus) {
            try {
                statusFiltro = StatusPedido.fromValor(statusLimpo);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Status inválido: " + statusLimpo);
            }
        }

        if (temStatus && temNome) {
            resultado = pedidoRepository.findByStatusAndNomeContainingIgnoreCase(statusFiltro, nomeLimpo, pageable);
        } else if (temStatus) {
            resultado = pedidoRepository.findByStatus(statusFiltro, pageable);
        } else if (temNome) {
            resultado = pedidoRepository.findByNomeContainingIgnoreCase(nomeLimpo, pageable);
        } else {
            resultado = pedidoRepository.findAll(pageable);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("pedidos", pedidoMapper.toResponseList(resultado.getContent()));
        resp.put("total", resultado.getTotalElements());
        resp.put("paginas", resultado.getTotalPages());
        resp.put("pagina", page);
        return resp;
    }

    // GET /api/admin/pedidos/pagos
    @Operation(summary = "Listar pedidos pagos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos com status pago")
    })
    @GetMapping("/pedidos/pagos")
    public Map<String, Object> pedidosPagos() {
        var pedidos = pedidoRepository.findByStatusOrderByPagoEmDesc(StatusPedido.PAGO);
        return Map.of("total", pedidos.size(), "pedidos", pedidoMapper.toResponseList(pedidos));
    }

    // POST /api/admin/pedidos/{id}/confirmar
    @Operation(summary = "Confirmar pagamento manualmente", description = "Marca como pago, garante as mesas e gera o QR Code do ingresso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido confirmado + QR Code"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "409", description = "Já confirmado, cancelado ou mesa indisponível")
    })
    @PostMapping("/pedidos/{id}/confirmar")
    public Map<String, Object> confirmar(@PathVariable String id) {
        var resultado = pedidoService.confirmarManual(id);
        return Map.of("ok", true,
                "qrcodeBase64", resultado.getQrcodeBase64(),
                "qrcodeToken", resultado.getQrcodeToken(),
                "pedido", resultado.getPedido());
    }

    // GET /api/admin/pedidos/{id}
    @Operation(summary = "Buscar pedido por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @GetMapping("/pedidos/{id}")
    public Map<String, Object> buscarPorId(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        return Map.of("pedido", pedidoMapper.toResponse(pedido));
    }

    // GET /api/admin/pedidos/{id}/ingresso — regera QRCode para download (token existente)
    @Operation(summary = "Obter ingresso (QR Code)", description = "Regera a imagem do QR Code a partir do token existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido + QR Code em data URL"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido ainda não confirmado")
    })
    @GetMapping("/pedidos/{id}/ingresso")
    public Map<String, Object> ingresso(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (pedido.getStatus() != StatusPedido.PAGO) throw ApiException.conflict("Pedido ainda não confirmado.");
        String qrcodeBase64 = QrCodeUtil.toDataUrl(pedido.getQrcodeToken(), 300);
        return Map.of("pedido", pedidoMapper.toResponse(pedido), "qrcode_base64", qrcodeBase64);
    }

    // POST /api/admin/pedidos/{id}/ingresso/regenerar — gera novo token e novo QRCode
    @Operation(summary = "Regenerar ingresso", description = "Gera um novo token (invalida o anterior) e zera o status de uso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Novo QR Code"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido ainda não confirmado")
    })
    @PostMapping("/pedidos/{id}/ingresso/regenerar")
    public Map<String, Object> regenerarIngresso(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (pedido.getStatus() != StatusPedido.PAGO) throw ApiException.conflict("Pedido ainda não confirmado.");

        byte[] buf = new byte[20];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) sb.append(String.format("%02x", b));
        String qrcodeToken = sb.toString();

        pedido.setQrcodeToken(qrcodeToken);
        pedido.setQrcodeUsado(false);
        pedido.setQrcodeUsadoEm(null);
        pedidoRepository.save(pedido);

        String qrcodeBase64 = QrCodeUtil.toDataUrl(qrcodeToken, 300);
        return Map.of("pedido", pedidoMapper.toResponse(pedido), "qrcode_base64", qrcodeBase64);
    }

    // POST /api/admin/pedidos/{id}/cancelar
    @Operation(summary = "Cancelar pedido", description = "Libera as mesas e invalida o QR Code. Idempotente (ja_cancelado = true).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @PostMapping("/pedidos/{id}/cancelar")
    public Map<String, Object> cancelar(@PathVariable String id) {
        var resultado = pedidoService.cancelarPedido(id);
        return Map.of("ok", true,
                "pedido", resultado.pedido(),
                "mesas_liberadas", resultado.mesasLiberadas(),
                "ja_cancelado", resultado.jaCancelado());
    }

    // PUT /api/admin/pedidos/{id} — editar dados completos do pedido
    @Operation(summary = "Editar pedido", description = "Atualiza dados do cliente e, se informado, itens/mesas (recalcula o valor).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @PutMapping("/pedidos/{id}")
    public Map<String, Object> editar(@PathVariable String id, @RequestBody PedidoRequest body) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));

        String nome = ValidationUtil.cleanString(body.getNome(), 120);
        String telefone = ValidationUtil.onlyDigits(body.getTelefone(), 11);
        String cpf = ValidationUtil.onlyDigits(body.getCpf(), 11);

        if (!nome.isEmpty()) pedido.setNome(nome);
        if (!telefone.isEmpty()) pedido.setTelefone(telefone);
        if (!cpf.isEmpty()) pedido.setCpf(cpf);

        if (body.getItens() != null && !body.getItens().isEmpty()) {
            List<ItemNormalizado> itens = new ArrayList<>();
            BigDecimal valorTotal = BigDecimal.ZERO;
            for (var item : body.getItens()) {
                String produto = ValidationUtil.cleanString(item.getProduto(), 20);
                if (!PRECOS.containsKey(produto)) throw ApiException.badRequest("Produto inválido: " + produto);
                int quantidade = item.getQuantidade() == null ? 0 : item.getQuantidade();
                valorTotal = valorTotal.add(PRECOS.get(produto).multiply(BigDecimal.valueOf(quantidade)));
                itens.add(new ItemNormalizado(produto, quantidade));
            }

            try {
                pedido.setItensJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(itens));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            pedido.setProduto(com.pixevent.entity.Produto.fromValor(itens.get(0).getProduto()));
            pedido.setQuantidade(itens.get(0).getQuantidade());
            pedido.setValorTotal(valorTotal.setScale(2, RoundingMode.HALF_UP));

            int qtdMesas = itens.stream().filter(i -> i.getProduto().equals("mesa")).mapToInt(ItemNormalizado::getQuantidade).sum();
            List<String> mesasAtuais = normalizarListaMesas(pedido.getMesaNumero());

            if (qtdMesas > 0) {
                List<String> novasMesas = normalizarListaMesas(body.getMesa_numero());
                if (novasMesas.isEmpty()) novasMesas = mesaService.findDisponiveis(qtdMesas);

                if (novasMesas.size() != qtdMesas) throw ApiException.badRequest("Selecione exatamente " + qtdMesas + " mesa(s).");
                if (new LinkedHashSet<>(novasMesas).size() != novasMesas.size()) throw ApiException.badRequest("Há mesas repetidas na seleção.");

                List<String> finalNovasMesas = novasMesas;
                mesaService.liberarMuitas(mesasAtuais.stream().filter(n -> !finalNovasMesas.contains(n)).toList());

                for (String numero : novasMesas.stream().filter(n -> !mesasAtuais.contains(n)).toList()) {
                    boolean ok = mesaService.reservar(numero, pedido.getId());
                    if (!ok) throw new RuntimeException("Mesa " + numero + " indisponível.");
                }
                pedido.setMesaNumero(String.join(",", novasMesas));
            } else if (!mesasAtuais.isEmpty()) {
                mesaService.liberarMuitas(mesasAtuais);
                pedido.setMesaNumero(null);
            }
        }

        pedidoRepository.save(pedido);
        return Map.of("ok", true, "pedido", pedidoMapper.toResponse(pedido));
    }

    // DELETE /api/admin/pedidos/{id}
    @Operation(summary = "Excluir pedido", description = "Libera as mesas e remove o pedido definitivamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido excluído"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @DeleteMapping("/pedidos/{id}")
    public Map<String, Object> excluir(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        int liberadas = mesaService.liberarDoPedido(id);
        if (liberadas == 0) mesaService.liberarMuitas(normalizarListaMesas(pedido.getMesaNumero()));
        pedidoRepository.delete(pedido);
        return Map.of("ok", true);
    }

    // POST /api/admin/pedidos — inserir pedido manualmente
    @Operation(summary = "Inserir pedido manualmente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "Mesa já reservada")
    })
    @PostMapping("/pedidos")
    public Map<String, Object> inserir(@RequestBody PedidoRequest body) {
        String nome = ValidationUtil.cleanString(body.getNome(), 120);
        String telefone = ValidationUtil.onlyDigits(body.getTelefone(), 11);
        String cpf = ValidationUtil.onlyDigits(body.getCpf(), 11);

        if (nome.isEmpty() || cpf.isEmpty() || body.getItens() == null || body.getItens().isEmpty()) {
            throw ApiException.badRequest("nome, cpf e itens são obrigatórios.");
        }

        List<ItemNormalizado> itens = body.getItens().stream()
                .map(i -> new ItemNormalizado(ValidationUtil.cleanString(i.getProduto(), 20),
                        i.getQuantidade() == null ? 0 : i.getQuantidade()))
                .toList();

        var resultado = pedidoService.registrarPedido(nome, telefone, cpf, itens, body.getMesa_numero());
        return Map.of("ok", true, "id", resultado.getId(), "valorTotal", resultado.getValorTotal(), "mesas", resultado.getMesas());
    }

    // GET /api/admin/entradas — lista convidados que já entraram
    @Operation(summary = "Listar entradas (check-ins)", description = "Convidados cujo QR Code já foi validado, em ordem de entrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de entradas")
    })
    @GetMapping("/entradas")
    public Map<String, Object> entradas() {
        var entradas = pedidoRepository.findByQrcodeUsadoTrueOrderByQrcodeUsadoEmAsc();
        return Map.of("entradas", pedidoMapper.toResponseList(entradas));
    }

    private List<String> normalizarListaMesas(Object valor) {
        if (valor == null) return List.of();
        if (valor instanceof List<?> lista) {
            return lista.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String s = String.valueOf(valor);
        if (s.isBlank() || s.equals("null")) return List.of();
        return List.of(s.split(",")).stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
