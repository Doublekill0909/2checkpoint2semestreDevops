package fiap.dimdim.dto;

import fiap.dimdim.entity.Conta;
import fiap.dimdim.enums.TipoConta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Dados de entrada de uma conta, usados pelo formulário (/contas) e pela API
 * (/api/contas).
 *
 * Agência, número e data de abertura não fazem parte do formulário: o sistema
 * os define na abertura da conta e eles nunca mudam. O titular (clienteId)
 * também é fixado na abertura; em uma atualização ele precisa ser o mesmo.
 */
public class ContaForm {

    @NotNull(message = "Selecione o cliente titular.")
    private Long clienteId;

    @NotNull(message = "Selecione o tipo da conta.")
    private TipoConta tipo;

    @NotNull(message = "Informe o saldo.")
    @DecimalMin(value = "0.00", message = "O saldo não pode ser negativo.")
    @Digits(integer = 13, fraction = 2, message = "O saldo aceita até 13 dígitos inteiros e 2 casas decimais.")
    private BigDecimal saldo = BigDecimal.ZERO;

    private boolean ativa = true;

    public static ContaForm de(Conta conta) {
        ContaForm form = new ContaForm();
        form.setClienteId(conta.getCliente().getId());
        form.setTipo(conta.getTipo());
        form.setSaldo(conta.getSaldo());
        form.setAtiva(conta.isAtiva());
        return form;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public TipoConta getTipo() {
        return tipo;
    }

    public void setTipo(TipoConta tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }
}
