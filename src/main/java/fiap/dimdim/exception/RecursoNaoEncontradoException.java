package fiap.dimdim.exception;

/** O id informado não existe no banco. Vira 404 na API e na tela. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
