package fiap.dimdim.enums;

/**
 * Modalidades de conta oferecidas pelo DimDim. O nome da constante é o valor
 * gravado na coluna tb_conta.tipo, protegida pela constraint ck_conta_tipo.
 */
public enum TipoConta {

    CORRENTE("Conta corrente"),
    POUPANCA("Conta poupança"),
    SALARIO("Conta salário");

    private final String descricao;

    TipoConta(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
