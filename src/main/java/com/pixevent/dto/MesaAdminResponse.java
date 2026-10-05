package com.pixevent.dto;

import com.pixevent.entity.StatusMesa;

import java.time.OffsetDateTime;

public record MesaAdminResponse(String numero, StatusMesa status, String pedidoId, OffsetDateTime reservadoEm) {
}
