package com.pixevent.dto;

import com.pixevent.entity.Produto;
import com.pixevent.entity.StatusPedido;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PedidoResponse(
        String id,
        String cpf,
        String nome,
        String email,
        String telefone,
        Produto produto,
        Integer quantidade,
        String mesaNumero,
        BigDecimal valorTotal,
        String metodoPgto,
        StatusPedido status,
        String qrcodeToken,
        Boolean qrcodeUsado,
        OffsetDateTime qrcodeUsadoEm,
        String itensJson,
        OffsetDateTime pagoEm,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}