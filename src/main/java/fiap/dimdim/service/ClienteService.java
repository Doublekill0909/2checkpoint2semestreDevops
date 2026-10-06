package fiap.dimdim.service;

import fiap.dimdim.dto.ClienteForm;
import fiap.dimdim.dto.ClienteResponse;
import fiap.dimdim.entity.Cliente;
import fiap.dimdim.exception.RecursoNaoEncontradoException;
import fiap.dimdim.exception.RegraNegocioException;
import fiap.dimdim.repository.ClienteRepository;
import fiap.dimdim.repository.ContaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Regras de negócio do cadastro de clientes.
 *
 * Os logs registram apenas o id do cliente, nunca CPF ou e-mail: eles chegam
 * ao Application Insights como traces e não devem carregar dado pessoal.
 */
@Service
@Transactional(readOnly = true)
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clientes;
    private final ContaRepository contas;

    public ClienteService(ClienteRepository clientes, ContaRepository contas) {
        this.clientes = clientes;
        this.contas = contas;
    }

    public List<ClienteResponse> listar() {
        Map<Long, Long> contasPorCliente = contas.contarContasPorCliente().stream()
                .collect(Collectors.toMap(
                        ContaRepository.ContasPorCliente::getClienteId,
                        ContaRepository.ContasPorCliente::getQuantidade));
        return clientes.findAllByOrderByNomeAsc().stream()
                .map(cliente -> ClienteResponse.de(cliente, contasPorCliente.getOrDefault(cliente.getId(), 0L)))
                .toList();
    }

    public ClienteResponse buscar(Long id) {
        return ClienteResponse.de(obter(id), contas.countByClienteId(id));
    }

    public ClienteForm formulario(Long id) {
        return ClienteForm.de(obter(id));
    }

    @Transactional
    public ClienteResponse criar(ClienteForm form) {
        garantirUnicidade(form, null);
        Cliente cliente = new Cliente();
        form.aplicarEm(cliente);
        Cliente salvo = clientes.save(cliente);
        log.info("Cliente criado: id={}", salvo.getId());
        return ClienteResponse.de(salvo, 0);
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteForm form) {
        Cliente cliente = obter(id);
        garantirUnicidade(form, id);
        form.aplicarEm(cliente);
        // flush explícito: o UPDATE vai ao banco aqui, e não só no commit, para
        // que uma violação de constraint seja reportada por esta chamada.
        clientes.flush();
        log.info("Cliente atualizado: id={}", id);
        return ClienteResponse.de(cliente, contas.countByClienteId(id));
    }

    /**
     * A chave estrangeira fk_conta_cliente já impediria a exclusão de um
     * titular com contas; a verificação aqui existe para devolver uma
     * mensagem clara em vez de um erro de integridade do banco.
     */
    @Transactional
    public void excluir(Long id) {
        Cliente cliente = obter(id);
        long quantidade = contas.countByClienteId(id);
        if (quantidade > 0) {
            throw new RegraNegocioException(
                    "O cliente " + cliente.getNome() + " é titular de " + quantidade
                            + " conta(s). Exclua as contas antes de excluir o cliente.");
        }
        clientes.delete(cliente);
        clientes.flush();
        log.info("Cliente excluido: id={}", id);
    }

    private Cliente obter(Long id) {
        return clientes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente " + id + " não encontrado."));
    }

    private void garantirUnicidade(ClienteForm form, Long idAtual) {
        boolean cpfEmUso = idAtual == null
                ? clientes.existsByCpf(form.getCpf())
                : clientes.existsByCpfAndIdNot(form.getCpf(), idAtual);
        if (cpfEmUso) {
            throw new RegraNegocioException("cpf", "Já existe um cliente com este CPF.");
        }
        boolean emailEmUso = idAtual == null
                ? clientes.existsByEmail(form.getEmail())
                : clientes.existsByEmailAndIdNot(form.getEmail(), idAtual);
        if (emailEmUso) {
            throw new RegraNegocioException("email", "Já existe um cliente com este e-mail.");
        }
    }
}
