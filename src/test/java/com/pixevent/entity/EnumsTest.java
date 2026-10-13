package com.pixevent.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumsTest {

    @Test
    void statusPedido_fromValor_ignoraCaixa() {
        assertThat(StatusPedido.fromValor("pago")).isEqualTo(StatusPedido.PAGO);
        assertThat(StatusPedido.fromValor("AGUARDANDO_PIX")).isEqualTo(StatusPedido.AGUARDANDO_PIX);
        assertThat(StatusPedido.PAGO.getValor()).isEqualTo("pago");
    }

    @Test
    void statusPedido_valorInvalido() {
        assertThatThrownBy(() -> StatusPedido.fromValor("xyz")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusMesa_fromValor() {
        assertThat(StatusMesa.fromValor("Reservada")).isEqualTo(StatusMesa.RESERVADA);
        assertThat(StatusMesa.DISPONIVEL.getValor()).isEqualTo("disponivel");
        assertThatThrownBy(() -> StatusMesa.fromValor("livre")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void produto_fromValor() {
        assertThat(Produto.fromValor("MESA")).isEqualTo(Produto.MESA);
        assertThat(Produto.INGRESSO.getValor()).isEqualTo("ingresso");
        assertThatThrownBy(() -> Produto.fromValor("pizza")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversores_idaEVolta() {
        assertThat(new ProdutoConverter().convertToDatabaseColumn(Produto.MESA)).isEqualTo("mesa");
        assertThat(new ProdutoConverter().convertToEntityAttribute("mesa")).isEqualTo(Produto.MESA);
        assertThat(new ProdutoConverter().convertToDatabaseColumn(null)).isNull();
        assertThat(new ProdutoConverter().convertToEntityAttribute(null)).isNull();

        assertThat(new StatusPedidoConverter().convertToDatabaseColumn(StatusPedido.PAGO)).isEqualTo("pago");
        assertThat(new StatusPedidoConverter().convertToEntityAttribute("cancelado")).isEqualTo(StatusPedido.CANCELADO);
        assertThat(new StatusPedidoConverter().convertToDatabaseColumn(null)).isNull();

        assertThat(new StatusMesaConverter().convertToDatabaseColumn(StatusMesa.RESERVADA)).isEqualTo("reservada");
        assertThat(new StatusMesaConverter().convertToEntityAttribute("disponivel")).isEqualTo(StatusMesa.DISPONIVEL);
        assertThat(new StatusMesaConverter().convertToEntityAttribute(null)).isNull();
    }
}
