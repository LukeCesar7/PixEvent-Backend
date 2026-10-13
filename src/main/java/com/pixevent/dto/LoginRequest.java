package com.pixevent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Credenciais do administrador")
public class LoginRequest {
    @Schema(description = "Senha do admin (ADMIN_PASSWORD)", example = "minha-senha")
    private String senha;
}
