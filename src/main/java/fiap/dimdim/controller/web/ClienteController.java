package fiap.dimdim.controller.web;

import fiap.dimdim.dto.ClienteForm;
import fiap.dimdim.dto.ClienteResponse;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas do CRUD de clientes.
 *
 * Formulários HTML só enviam GET e POST, por isso a atualização é um POST em
 * /clientes/{id} e a exclusão um POST em /clientes/{id}/excluir. Depois de
 * cada operação a tela redireciona (padrão Post/Redirect/Get), o que evita
 * reenviar o formulário ao atualizar a página.
 */
@Controller
@RequestMapping("/clientes")
public class ClienteController {

    private static final String FORMULARIO = "clientes/form";

    private final ClienteService clienteService;
    private final ContaService contaService;

    public ClienteController(ClienteService clienteService, ContaService contaService) {
        this.clienteService = clienteService;
        this.contaService = contaService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("clientes", clienteService.listar());
        return "clientes/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("cliente", new ClienteForm());
        return FORMULARIO;
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("cliente") ClienteForm form, BindingResult validacao,
                        RedirectAttributes redirect) {
        if (validacao.hasErrors()) {
            return FORMULARIO;
        }
        try {
            ClienteResponse criado = clienteService.criar(form);
            redirect.addFlashAttribute("sucesso",
                    "Cliente " + criado.nome() + " cadastrado com o id " + criado.id() + ".");
            return "redirect:/clientes/" + criado.id();
        } catch (RegraNegocioException e) {
            Validacoes.rejeitar(validacao, e);
            return FORMULARIO;
        }
    }

    @GetMapping("/{id}")
    public String detalhar(@PathVariable("id") Long id, Model model) {
        model.addAttribute("cliente", clienteService.buscar(id));
        model.addAttribute("contas", contaService.listar(id));
        return "clientes/detalhe";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long id, Model model) {
        model.addAttribute("cliente", clienteService.formulario(id));
        model.addAttribute("clienteId", id);
        return FORMULARIO;
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable("id") Long id,
                            @Valid @ModelAttribute("cliente") ClienteForm form, BindingResult validacao,
                            Model model, RedirectAttributes redirect) {
        model.addAttribute("clienteId", id);
        if (validacao.hasErrors()) {
            return FORMULARIO;
        }
        try {
            ClienteResponse atualizado = clienteService.atualizar(id, form);
            redirect.addFlashAttribute("sucesso", "Cliente " + atualizado.nome() + " atualizado.");
            return "redirect:/clientes/" + id;
        } catch (RegraNegocioException e) {
            Validacoes.rejeitar(validacao, e);
            return FORMULARIO;
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable("id") Long id, RedirectAttributes redirect) {
        try {
            clienteService.excluir(id);
            redirect.addFlashAttribute("sucesso", "Cliente " + id + " excluído.");
            return "redirect:/clientes";
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/" + id;
        }
    }
}
