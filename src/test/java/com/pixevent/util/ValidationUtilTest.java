package com.pixevent.util;

import com.pixevent.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationUtilTest {

    @Test
    void cleanString_removeAngulosEControleETrim() {
        assertThat(ValidationUtil.cleanString("  <b>Ola</b>\u0000\n ", 50)).isEqualTo("bOla/b");
    }

    @Test
    void cleanString_truncaNoMaximo() {
        assertThat(ValidationUtil.cleanString("abcdefghij", 4)).isEqualTo("abcd");
    }

    @Test
    void cleanString_nullViraVazio() {
        assertThat(ValidationUtil.cleanString(null, 10)).isEmpty();
    }

    @Test
    void cleanString_aceitaObjetoNaoString() {
        assertThat(ValidationUtil.cleanString(123, 10)).isEqualTo("123");
    }

    @Test
    void onlyDigits_mantemSomenteNumeros() {
        assertThat(ValidationUtil.onlyDigits("123.456.789-01", 11)).isEqualTo("12345678901");
    }

    @Test
    void onlyDigits_truncaNoMaximo() {
        assertThat(ValidationUtil.onlyDigits("(85) 99999-8888 ramal 12", 11)).isEqualTo("85999998888");
    }

    @Test
    void onlyDigits_nullViraVazio() {
        assertThat(ValidationUtil.onlyDigits(null, 11)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void validarCadastro_nomeObrigatorio(String nome) {
        assertThatThrownBy(() -> ValidationUtil.validarCadastro(nome, "12345678901", ""))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("Nome é obrigatório.");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "123456789012"})
    void validarCadastro_cpfPrecisaTer11Digitos(String cpf) {
        assertThatThrownBy(() -> ValidationUtil.validarCadastro("Maria", cpf, ""))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("CPF inválido.");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "123456789012"})
    void validarCadastro_telefoneInvalido(String tel) {
        assertThatThrownBy(() -> ValidationUtil.validarCadastro("Maria", "12345678901", tel))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Telefone inválido."));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"8533334444", "85999998888"})
    void validarCadastro_telefoneOpcionalOuComDezOuOnzeDigitos(String tel) {
        ValidationUtil.validarCadastro("Maria", "12345678901", tel); // não deve lançar
    }
}
