package com.pixevent.dto;


public record CancelarResultado(PedidoResponse pedido, int mesasLiberadas, boolean jaCancelado) {
}
