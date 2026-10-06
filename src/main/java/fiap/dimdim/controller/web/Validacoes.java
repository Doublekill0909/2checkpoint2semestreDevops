package fiap.dimdim.controller.web;

import fiap.dimdim.exception.RegraNegocioException;
import org.springframework.validation.BindingResult;

/** Converte uma regra de negócio violada em erro exibível no formulário. */
final class Validacoes {

    private Validacoes() {
    }

    /**
     * Se a regra aponta um campo, o erro aparece ao lado dele; caso contrário,
     * aparece no topo do formulário como erro global.
     */
    static void rejeitar(BindingResult validacao, RegraNegocioException e) {
        if (e.getCampo() != null) {
            validacao.rejectValue(e.getCampo(), "regraNegocio", e.getMessage());
        } else {
            validacao.reject("regraNegocio", e.getMessage());
        }
    }
}
