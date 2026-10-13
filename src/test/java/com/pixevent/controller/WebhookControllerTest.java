package com.pixevent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    @Test
    void mercadoPago_respondeSempre200() throws Exception {
        MockMvcBuilders.standaloneSetup(new WebhookController()).build()
                .perform(post("/webhook/mp").contentType("application/json").content("{\"x\":1}"))
                .andExpect(status().isOk());
    }
}
