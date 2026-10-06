package fiap.dimdim.dto;

import fiap.dimdim.entity.Conta;
import fiap.dimdim.enums.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Conta como é devolvida pela API e exibida nas telas, já com o nome do
 * titular, que vem do JOIN com tb_cliente.
 */
public record ContaResponse(
        Long id,
        String agencia,
        String numero,
        TipoConta tipo,
        BigDecimal saldo,
        boolean ativa,
        LocalDate dataAbertura,
        Long clienteId,
        String clienteNome) {

    public static ContaResponse de(Conta conta) {
        return new ContaResponse(
                conta.getId(),
                conta.getAgencia(),
                conta.getNumero(),
                conta.getTipo(),
                conta.getSaldo(),
                conta.isAtiva(),
                conta.getDataAbertura(),
                conta.getCliente().getId(),
                conta.getCliente().getNome());
    }
}
