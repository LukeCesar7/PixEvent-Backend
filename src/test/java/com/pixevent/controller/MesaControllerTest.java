package com.pixevent.controller;

import com.pixevent.dto.MesaResponse;
import com.pixevent.entity.Mesa;
import com.pixevent.entity.StatusMesa;
import com.pixevent.mapper.MesaMapper;
import com.pixevent.service.MesaService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MesaControllerTest {

    @Test
    void listar_retornaMesasMapeadas() throws Exception {
        MesaService service = mock(MesaService.class);
        MesaMapper mapper = mock(MesaMapper.class);
        List<Mesa> mesas = List.of(Mesa.builder().numero("1").build());
        when(service.findAll()).thenReturn(mesas);
        when(mapper.toResponseList(mesas)).thenReturn(List.of(
                new MesaResponse("1", StatusMesa.DISPONIVEL), new MesaResponse("2", StatusMesa.RESERVADA)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MesaController(service, mapper)).build();

        mvc.perform(get("/api/mesas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("1"))
                .andExpect(jsonPath("$[0].status").value("disponivel"))
                .andExpect(jsonPath("$[1].status").value("reservada"));
    }
}
