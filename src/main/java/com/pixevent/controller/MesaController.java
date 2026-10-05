package com.pixevent.controller;

import com.pixevent.dto.MesaResponse;
import com.pixevent.mapper.MesaMapper;
import com.pixevent.service.MesaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mesas")
public class MesaController {

    private final MesaService mesaService;
    private final MesaMapper mesaMapper;

    public MesaController(MesaService mesaService, MesaMapper mesaMapper) {
        this.mesaService = mesaService;
        this.mesaMapper = mesaMapper;
    }

    @GetMapping
    public List<MesaResponse> listar() {
        return mesaMapper.toResponseList(mesaService.findAll());
    }
}

