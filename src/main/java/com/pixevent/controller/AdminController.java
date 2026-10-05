package com.pixevent.controller;

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
    @GetMapping("/metricas")
    public Map<String, Object> metricas() {
        var stats = pedidoRepository.contarPorStatus();
        var mesas = mesaRepository.findAllByOrderByNumeroAsc();
        long totalRifas = rifaService.contarTotal();
        return Map.of("stats", stats, "mesas", mesaMapper.toAdminResponseList(mesas), "total_rifas", totalRifas);
    }

    // GET /api/admin/pedidos?status=pago&nome=luqui&page=1
    @GetMapping("/pedidos")
    public Map<String, Object> listarPedidos(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String nome) {
        int limit = 50;
        String statusLimpo = ValidationUtil.cleanString(status, 30);
        String nomeLimpo = ValidationUtil.cleanString(nome, 80);
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by(Sort.Direction.DESC, "criadoEm"));

        Page<Pedido> resultado;
        boolean temStatus = !statusLimpo.isBlank();
        boolean temNome = !nomeLimpo.isBlank();

        if (temStatus && temNome) {
            resultado = pedidoRepository.findByStatusAndNomeContainingIgnoreCase(
                    StatusPedido.fromValor(statusLimpo), nomeLimpo, pageable);
        } else if (temStatus) {
            resultado = pedidoRepository.findByStatus(StatusPedido.fromValor(statusLimpo), pageable);
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
    @GetMapping("/pedidos/pagos")
    public Map<String, Object> pedidosPagos() {
        var pedidos = pedidoRepository.findByStatusOrderByPagoEmDesc(StatusPedido.PAGO);
        return Map.of("total", pedidos.size(), "pedidos", pedidoMapper.toResponseList(pedidos));
    }

    // POST /api/admin/pedidos/{id}/confirmar
    @PostMapping("/pedidos/{id}/confirmar")
    public Map<String, Object> confirmar(@PathVariable String id) {
        var resultado = pedidoService.confirmarManual(id);
        return Map.of("ok", true,
                "qrcodeBase64", resultado.getQrcodeBase64(),
                "qrcodeToken", resultado.getQrcodeToken(),
                "pedido", resultado.getPedido());
    }

    // GET /api/admin/pedidos/{id}
    @GetMapping("/pedidos/{id}")
    public Map<String, Object> buscarPorId(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        return Map.of("pedido", pedidoMapper.toResponse(pedido));
    }

    // GET /api/admin/pedidos/{id}/ingresso — re-gera QR Code para download (token existente)
    @GetMapping("/pedidos/{id}/ingresso")
    public Map<String, Object> ingresso(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (pedido.getStatus() != StatusPedido.PAGO) throw ApiException.conflict("Pedido ainda não confirmado.");
        String qrcodeBase64 = QrCodeUtil.toDataUrl(pedido.getQrcodeToken(), 300);
        return Map.of("pedido", pedidoMapper.toResponse(pedido), "qrcode_base64", qrcodeBase64);
    }

    // POST /api/admin/pedidos/{id}/ingresso/regenerar — gera novo token e novo QR Code
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
    @PostMapping("/pedidos/{id}/cancelar")
    public Map<String, Object> cancelar(@PathVariable String id) {
        var resultado = pedidoService.cancelarPedido(id);
        return Map.of("ok", true,
                "pedido", resultado.pedido(),
                "mesas_liberadas", resultado.mesasLiberadas(),
                "ja_cancelado", resultado.jaCancelado());
    }

    // PUT /api/admin/pedidos/{id} — editar dados completos do pedido
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
    @DeleteMapping("/pedidos/{id}")
    public Map<String, Object> excluir(@PathVariable String id) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        int liberadas = mesaService.liberarDoPedido(id);
        if (liberadas == 0) mesaService.liberarMuitas(normalizarListaMesas(pedido.getMesaNumero()));
        pedidoRepository.delete(pedido);
        return Map.of("ok", true);
    }

    // POST /api/admin/pedidos — inserir pedido manualmente
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
