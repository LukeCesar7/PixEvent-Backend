package com.pixevent.controller;

import com.pixevent.dto.PedidoResponse;

/** Fábrica de objetos de apoio aos testes de controller. */
final class TestData {

    private TestData() {
    }

    static PedidoResponse resp(String id) {
        return new PedidoResponse(id, null, "Maria", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
