package fiap.dimdim.controller.api;

import fiap.dimdim.dto.ClienteForm;
import fiap.dimdim.dto.ClienteResponse;
import fiap.dimdim.service.ClienteService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * API REST de clientes. Usa as mesmas regras de negócio das telas, de modo que
 * um registro criado por aqui aparece imediatamente no front end e vice-versa.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteApiController {

    private final ClienteService clienteService;

    public ClienteApiController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable("id") Long id) {
        return clienteService.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteForm form, UriComponentsBuilder uri) {
        ClienteResponse criado = clienteService.criar(form);
        return ResponseEntity
                .created(uri.path("/api/clientes/{id}").buildAndExpand(criado.id()).toUri())
                .body(criado);
    }

    @PutMapping("/{id}")
    public ClienteResponse atualizar(@PathVariable("id") Long id, @Valid @RequestBody ClienteForm form) {
        return clienteService.atualizar(id, form);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable("id") Long id) {
        clienteService.excluir(id);
    }
}
