package fiap.dimdim.dto;

import fiap.dimdim.entity.Cliente;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cliente como é devolvido pela API e exibido nas telas. */
public record ClienteResponse(
        Long id,
        String nome,
        String cpf,
        String email,
        String telefone,
        LocalDate dataNascimento,
        LocalDateTime dataCadastro,
        long quantidadeContas) {

    public static ClienteResponse de(Cliente cliente, long quantidadeContas) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getCpf(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getDataNascimento(),
                cliente.getDataCadastro(),
                quantidadeContas);
    }
}
