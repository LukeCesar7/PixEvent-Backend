package com.pixevent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.pixevent.dto.MesaResponse;
import com.pixevent.mapper.MesaMapper;
import com.pixevent.service.MesaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mesas")
@Tag(name = "Mesas", description = "Mapa de mesas")
public class MesaController {

    private final MesaService mesaService;
    private final MesaMapper mesaMapper;

    public MesaController(MesaService mesaService, MesaMapper mesaMapper) {
        this.mesaService = mesaService;
        this.mesaMapper = mesaMapper;
    }

    @Operation(summary = "Listar mesas", description = "Todas as mesas em ordem de número, com status disponivel/reservada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de mesas")
    })
    @GetMapping
    public List<MesaResponse> listar() {
        return mesaMapper.toResponseList(mesaService.findAll());
    }
}

