package fiap.dimdim.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Dígito verificador módulo 11 do número da conta (pesos 2 a 9, da direita para a esquerda). */
class ContaServiceDigitoTest {

    @ParameterizedTest(name = "{0}-{1}")
    @CsvSource({
            "12345, 5",
            "99999, 7",
            "10000, 5",
            "10002, 1",
            // resto 0 e resto 1 resultariam em 11 e 10, que viram 0
            "11000, 0",
            "10003, 0"
    })
    void calculaDigitoVerificador(int base, int esperado) {
        assertThat(ContaService.digitoVerificador(base)).isEqualTo(esperado);
    }
}
