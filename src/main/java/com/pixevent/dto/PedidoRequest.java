package com.pixevent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Dados para criar/editar um pedido")
public class PedidoRequest {
    @Schema(example = "Maria da Silva")
    private String nome;
    @Schema(description = "Somente dígitos (10 ou 11); opcional", example = "85999998888")
    private String telefone;
    @Schema(description = "11 dígitos", example = "12345678901")
    private String cpf;
    private List<ItemRequest> itens;
    @Schema(description = "Mesas desejadas: texto CSV (\"1,2\") ou lista de textos. Se omitido, o sistema escolhe as primeiras disponíveis.", example = "1,2")
    private Object mesa_numero; // pode vir como String (CSV) ou List<String>, igual ao Node
}
