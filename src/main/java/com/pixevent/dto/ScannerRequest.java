package com.pixevent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Leitura do QR Code")
public class ScannerRequest {
    @Schema(description = "Token lido do QR Code", example = "9f2c1d...")
    private String token;
}
