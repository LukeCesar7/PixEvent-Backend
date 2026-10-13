package com.pixevent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 Inerte - Em desenvolvimento
 */
@RestController
@RequestMapping("/webhook")
@Tag(name = "Webhook", description = "Reservado para a integração com Mercado Pago (ainda inerte)")
public class WebhookController {

    @Operation(summary = "Webhook Mercado Pago", description = "Inerte: apenas responde 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recebido")
    })
    @PostMapping("/mp")
    public ResponseEntity<Void> mercadoPago() {
        return ResponseEntity.ok().build();
    }
}
