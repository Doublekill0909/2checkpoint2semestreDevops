package fiap.dimdim.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatação brasileira usada pelos templates Thymeleaf como ${@fmt.moeda(...)}.
 *
 * O banco guarda os valores crus (CPF só com dígitos, saldo como DECIMAL); a
 * máscara é aplicada apenas na exibição.
 */
@Component("fmt")
public class Formatos {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor == null ? BigDecimal.ZERO : valor);
    }

    public String cpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    public String data(LocalDate data) {
        return data == null ? "" : DATA.format(data);
    }

    public String dataHora(LocalDateTime dataHora) {
        return dataHora == null ? "" : DATA_HORA.format(dataHora);
    }
}
