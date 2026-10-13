package com.pixevent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Linha do pedido")
public class ItemRequest {
    @Schema(description = "Produto: ingresso ou mesa", allowableValues = {"ingresso", "mesa"}, example = "ingresso")
    private String produto;
    @Schema(description = "Quantidade (1 a 400)", example = "2")
    private Integer quantidade;
}
