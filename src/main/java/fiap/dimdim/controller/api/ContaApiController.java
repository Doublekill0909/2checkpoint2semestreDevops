package fiap.dimdim.controller.api;

import fiap.dimdim.dto.ContaForm;
import fiap.dimdim.dto.ContaResponse;
import fiap.dimdim.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** API REST de contas. GET /api/contas?clienteId=1 filtra pelas contas de um titular. */
@RestController
@RequestMapping("/api/contas")
public class ContaApiController {

    private final ContaService contaService;

    public ContaApiController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping
    public List<ContaResponse> listar(@RequestParam(name = "clienteId", required = false) Long clienteId) {
        return contaService.listar(clienteId);
    }

    @GetMapping("/{id}")
    public ContaResponse buscar(@PathVariable("id") Long id) {
        return contaService.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody ContaForm form, UriComponentsBuilder uri) {
        ContaResponse criada = contaService.criar(form);
        return ResponseEntity
                .created(uri.path("/api/contas/{id}").buildAndExpand(criada.id()).toUri())
                .body(criada);
    }

    @PutMapping("/{id}")
    public ContaResponse atualizar(@PathVariable("id") Long id, @Valid @RequestBody ContaForm form) {
        return contaService.atualizar(id, form);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable("id") Long id) {
        contaService.excluir(id);
    }
}
