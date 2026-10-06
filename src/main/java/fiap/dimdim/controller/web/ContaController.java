package fiap.dimdim.controller.web;

import fiap.dimdim.dto.ContaForm;
import fiap.dimdim.dto.ContaResponse;
import fiap.dimdim.exception.RegraNegocioException;
import fiap.dimdim.service.ClienteService;
import fiap.dimdim.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas do CRUD de contas, seguindo o mesmo padrão de {@link ClienteController}. */
@Controller
@RequestMapping("/contas")
public class ContaController {

    private static final String FORMULARIO = "contas/form";

    private final ContaService contaService;
    private final ClienteService clienteService;

    public ContaController(ContaService contaService, ClienteService clienteService) {
        this.contaService = contaService;
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(@RequestParam(name = "clienteId", required = false) Long clienteId, Model model) {
        model.addAttribute("contas", contaService.listar(clienteId));
        if (clienteId != null) {
            model.addAttribute("titular", clienteService.buscar(clienteId));
        }
        return "contas/lista";
    }

    @GetMapping("/nova")
    public String nova(@RequestParam(name = "clienteId", required = false) Long clienteId, Model model) {
        ContaForm form = new ContaForm();
        form.setClienteId(clienteId);
        model.addAttribute("conta", form);
        return prepararFormulario(model, null);
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("conta") ContaForm form, BindingResult validacao,
                        Model model, RedirectAttributes redirect) {
        if (validacao.hasErrors()) {
            return prepararFormulario(model, null);
        }
        try {
            ContaResponse criada = contaService.criar(form);
            redirect.addFlashAttribute("sucesso", "Conta " + criada.agencia() + " / " + criada.numero()
                    + " aberta para " + criada.clienteNome() + " (id " + criada.id() + ").");
            return "redirect:/contas";
        } catch (RegraNegocioException e) {
            Validacoes.rejeitar(validacao, e);
            return prepararFormulario(model, null);
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long id, Model model) {
        model.addAttribute("conta", contaService.formulario(id));
        return prepararFormulario(model, id);
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable("id") Long id,
                            @Valid @ModelAttribute("conta") ContaForm form, BindingResult validacao,
                            Model model, RedirectAttributes redirect) {
        if (validacao.hasErrors()) {
            return prepararFormulario(model, id);
        }
        try {
            ContaResponse atualizada = contaService.atualizar(id, form);
            redirect.addFlashAttribute("sucesso",
                    "Conta " + atualizada.agencia() + " / " + atualizada.numero() + " atualizada.");
            return "redirect:/contas";
        } catch (RegraNegocioException e) {
            Validacoes.rejeitar(validacao, e);
            return prepararFormulario(model, id);
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable("id") Long id, RedirectAttributes redirect) {
        ContaResponse conta = contaService.buscar(id);
        contaService.excluir(id);
        redirect.addFlashAttribute("sucesso",
                "Conta " + conta.agencia() + " / " + conta.numero() + " de " + conta.clienteNome() + " excluída.");
        return "redirect:/contas";
    }

    /**
     * Na edição a conta existente é exibida (agência, número e titular, que
     * não mudam); na abertura, a lista de clientes alimenta o campo titular.
     */
    private String prepararFormulario(Model model, Long contaId) {
        if (contaId != null) {
            model.addAttribute("contaId", contaId);
            model.addAttribute("contaAtual", contaService.buscar(contaId));
        } else {
            model.addAttribute("clientes", clienteService.listar());
        }
        return FORMULARIO;
    }
}
