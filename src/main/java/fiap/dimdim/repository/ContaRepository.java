package fiap.dimdim.repository;

import fiap.dimdim.entity.Conta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * As consultas que devolvem contas para a tela carregam o titular junto
 * (@EntityGraph), em um único SELECT com JOIN. Como open-in-view está
 * desligado, acessar conta.getCliente() fora da transação falharia se o
 * titular não viesse carregado.
 */
public interface ContaRepository extends JpaRepository<Conta, Long> {

    @EntityGraph(attributePaths = "cliente")
    List<Conta> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = "cliente")
    List<Conta> findByClienteIdOrderByIdDesc(Long clienteId);

    @EntityGraph(attributePaths = "cliente")
    List<Conta> findTop5ByOrderByIdDesc();

    @EntityGraph(attributePaths = "cliente")
    Optional<Conta> findComTitularById(Long id);

    long countByClienteId(Long clienteId);

    long countByAtivaTrue();

    boolean existsByAgenciaAndNumero(String agencia, String numero);

    /** Soma dos saldos das contas ativas; devolve null quando não há nenhuma. */
    @Query("select sum(c.saldo) from Conta c where c.ativa = true")
    BigDecimal somarSaldoDasContasAtivas();

    /** Quantidade de contas por titular, em um único GROUP BY para a listagem de clientes. */
    @Query("select c.cliente.id as clienteId, count(c) as quantidade from Conta c group by c.cliente.id")
    List<ContasPorCliente> contarContasPorCliente();

    interface ContasPorCliente {
        Long getClienteId();

        Long getQuantidade();
    }
}
