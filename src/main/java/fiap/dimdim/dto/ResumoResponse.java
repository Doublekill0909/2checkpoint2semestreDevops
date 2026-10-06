package fiap.dimdim.dto;

import java.math.BigDecimal;
import java.util.List;

/** Números do painel inicial, calculados direto no banco a cada acesso. */
public record ResumoResponse(
        long totalClientes,
        long totalContas,
        long contasAtivas,
        BigDecimal saldoTotalContasAtivas,
        List<ContaResponse> ultimasContas) {
}
