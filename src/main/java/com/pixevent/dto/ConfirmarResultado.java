package com.pixevent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConfirmarResultado {
    private String qrcodeBase64;
    private String qrcodeToken;
    private PedidoResponse pedido;
}
