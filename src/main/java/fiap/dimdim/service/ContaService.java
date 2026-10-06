package fiap.dimdim.service;

import fiap.dimdim.dto.ContaForm;
import fiap.dimdim.dto.ContaResponse;
import fiap.dimdim.dto.ResumoResponse;
import fiap.dimdim.entity.Cliente;
import fiap.dimdim.entity.Conta;
import fiap.dimdim.exception.RecursoNaoEncontradoException;
import fiap.dimdim.exception.RegraNegocioException;
import fiap.dimdim.repository.ClienteRepository;
import fiap.dimdim.repository.ContaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Regras de negócio das contas bancárias. */
@Service
@Transactional(readOnly = true)
public class ContaService {

    /** O DimDim é um banco digital com agência única. */
    public static final String AGENCIA_PADRAO = "0001";

    private static final int TENTATIVAS_DE_NUMERO = 20;

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);

    private final ContaRepository contas;
    private final ClienteRepository clientes;

    public ContaService(ContaRepository contas, ClienteRepository clientes) {
        this.contas = contas;
        this.clientes = clientes;
    }

    /** Todas as contas ou, se {@code clienteId} vier preenchido, só as daquele titular. */
    public List<ContaResponse> listar(Long clienteId) {
        List<Conta> resultado = clienteId == null
                ? contas.findAllByOrderByIdDesc()
                : contas.findByClienteIdOrderByIdDesc(clienteId);
        return resultado.stream().map(ContaResponse::de).toList();
    }

    public ContaResponse buscar(Long id) {
        return ContaResponse.de(obter(id));
    }

    public ContaForm formulario(Long id) {
        return ContaForm.de(obter(id));
    }

    public ResumoResponse resumo() {
        BigDecimal saldo = contas.somarSaldoDasContasAtivas();
        return new ResumoResponse(
                clientes.count(),
                contas.count(),
                contas.countByAtivaTrue(),
                saldo == null ? BigDecimal.ZERO : saldo,
                contas.findTop5ByOrderByIdDesc().stream().map(ContaResponse::de).toList());
    }

    @Transactional
    public ContaResponse criar(ContaForm form) {
        Cliente titular = clientes.findById(form.getClienteId())
                .orElseThrow(() -> new RegraNegocioException("clienteId",
                        "Cliente titular " + form.getClienteId() + " não encontrado."));
        Conta conta = new Conta();
        conta.setCliente(titular);
        conta.setAgencia(AGENCIA_PADRAO);
        conta.setNumero(gerarNumero(AGENCIA_PADRAO));
        conta.setTipo(form.getTipo());
        conta.setSaldo(form.getSaldo());
        conta.setAtiva(form.isAtiva());
        Conta salva = contas.save(conta);
        log.info("Conta criada: id={} clienteId={}", salva.getId(), titular.getId());
        return ContaResponse.de(salva);
    }

    @Transactional
    public ContaResponse atualizar(Long id, ContaForm form) {
        Conta conta = obter(id);
        if (!Objects.equals(conta.getCliente().getId(), form.getClienteId())) {
            throw new RegraNegocioException("clienteId",
                    "O titular de uma conta não pode ser alterado. Abra uma nova conta para o outro cliente.");
        }
        conta.setTipo(form.getTipo());
        conta.setSaldo(form.getSaldo());
        conta.setAtiva(form.isAtiva());
        contas.flush();
        log.info("Conta atualizada: id={}", id);
        return ContaResponse.de(conta);
    }

    @Transactional
    public void excluir(Long id) {
        Conta conta = obter(id);
        contas.delete(conta);
        contas.flush();
        log.info("Conta excluida: id={}", id);
    }

    private Conta obter(Long id) {
        return contas.findComTitularById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta " + id + " não encontrada."));
    }

    /**
     * Gera um número no formato 00000-0 que ainda não exista na agência. O
     * dígito verificador é o módulo 11 dos cinco dígitos, como nos bancos
     * brasileiros. A constraint uk_conta_agencia_numero continua sendo a
     * garantia final contra duplicidade.
     */
    private String gerarNumero(String agencia) {
        for (int tentativa = 0; tentativa < TENTATIVAS_DE_NUMERO; tentativa++) {
            int base = ThreadLocalRandom.current().nextInt(10_000, 100_000);
            String numero = base + "-" + digitoVerificador(base);
            if (!contas.existsByAgenciaAndNumero(agencia, numero)) {
                return numero;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um número de conta livre.");
    }

    static int digitoVerificador(int base) {
        String digitos = String.valueOf(base);
        int soma = 0;
        int peso = 2;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(digitos.charAt(i)) * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = 11 - (soma % 11);
        return resto >= 10 ? 0 : resto;
    }
}
