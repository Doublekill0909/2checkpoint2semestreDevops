package fiap.dimdim.controller.web;

import fiap.dimdim.service.ContaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Painel inicial com os totais do banco. */
@Controller
public class InicioController {

    private final ContaService contaService;

    public InicioController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("resumo", contaService.resumo());
        return "index";
    }
}
