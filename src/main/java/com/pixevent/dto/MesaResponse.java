package com.pixevent.dto;

import com.pixevent.entity.StatusMesa;

public record MesaResponse(String numero, StatusMesa status) {
}
