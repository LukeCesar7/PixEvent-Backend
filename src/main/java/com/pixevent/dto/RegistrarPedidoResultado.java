package com.pixevent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "Pedido registrado")
public class RegistrarPedidoResultado {
    @Schema(example = "FN-123456")
    private String id;
    private BigDecimal valorTotal;
    private List<String> mesas;
}

