package fiap.dimdim.web;

import fiap.dimdim.exception.RecursoNaoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Erros das telas. Os da API são tratados em
 * fiap.dimdim.controller.api.TratamentoDeErrosApi.
 *
 * A view "error" é a mesma que o Spring Boot usa para os demais erros, de modo
 * que todas as falhas aparecem com o mesmo layout.
 */
@ControllerAdvice(basePackages = "fiap.dimdim.controller.web")
public class TratamentoDeErrosWeb {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String naoEncontrado(RecursoNaoEncontradoException e, Model model) {
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("error", "Não encontrado");
        model.addAttribute("message", e.getMessage());
        return "error";
    }
}
