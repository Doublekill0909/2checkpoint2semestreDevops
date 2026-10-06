package fiap.dimdim.controller.api;

import fiap.dimdim.exception.RecursoNaoEncontradoException;
import fiap.dimdim.exception.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Converte as exceções da API em respostas application/problem+json
 * (RFC 9457), com status coerente com a causa do erro.
 */
@RestControllerAdvice(basePackages = "fiap.dimdim.controller.api")
public class TratamentoDeErrosApi {

    private static final Logger log = LoggerFactory.getLogger(TratamentoDeErrosApi.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail regraNegocio(RegraNegocioException e) {
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "Regra de negócio violada", e.getMessage());
        if (e.getCampo() != null) {
            problema.setProperty("campo", e.getCampo());
        }
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        List<Map<String, String>> erros = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> Map.of(
                        "campo", erro.getField(),
                        "mensagem", String.valueOf(erro.getDefaultMessage())))
                .toList();
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Um ou mais campos não passaram na validação.");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "JSON inválido",
                "O corpo da requisição não é um JSON válido para este recurso.");
    }

    /**
     * Última barreira: uma constraint do banco (UNIQUE, FOREIGN KEY ou CHECK)
     * recusou a operação, por exemplo em duas requisições simultâneas com o
     * mesmo CPF. O detalhe técnico fica só no log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException e) {
        log.warn("Violacao de integridade no banco: {}", e.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflito de integridade",
                "O banco de dados recusou a operação por violar uma restrição de integridade.");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
