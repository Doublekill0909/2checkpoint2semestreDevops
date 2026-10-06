package fiap.dimdim.exception;

/**
 * A operação é válida no formato, mas viola uma regra do negócio, como um CPF
 * já cadastrado ou a exclusão de um cliente que ainda tem contas. Vira 409 na
 * API e mensagem de erro no formulário.
 *
 * Quando a regra diz respeito a um campo específico, {@link #getCampo()}
 * informa qual, para que a tela exiba o erro ao lado do campo certo.
 */
public class RegraNegocioException extends RuntimeException {

    private final String campo;

    public RegraNegocioException(String mensagem) {
        this(null, mensagem);
    }

    public RegraNegocioException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
