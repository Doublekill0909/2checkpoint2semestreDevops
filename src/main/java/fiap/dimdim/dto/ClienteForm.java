package fiap.dimdim.dto;

import fiap.dimdim.entity.Cliente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Dados de entrada de um cliente, usados tanto pelo formulário da tela
 * (/clientes) quanto pelo corpo JSON da API (/api/clientes).
 *
 * Os setters normalizam o que o usuário digita antes da validação: o CPF
 * perde a pontuação, o e-mail vai para minúsculas e espaços nas pontas
 * são descartados. Assim "123.456.789-09" e "12345678909" são o mesmo CPF.
 */
public class ClienteForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String nome;

    @NotBlank(message = "Informe o CPF.")
    @Pattern(regexp = "\\d{11}", message = "O CPF deve ter 11 dígitos.")
    private String cpf;

    @NotBlank(message = "Informe o e-mail.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 120, message = "O e-mail deve ter no máximo 120 caracteres.")
    private String email;

    @Pattern(regexp = "[0-9()+\\- ]{8,20}", message = "Informe um telefone com 8 a 20 caracteres (dígitos, espaço, parênteses, + ou -).")
    private String telefone;

    @NotNull(message = "Informe a data de nascimento.")
    @Past(message = "A data de nascimento deve estar no passado.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataNascimento;

    public static ClienteForm de(Cliente cliente) {
        ClienteForm form = new ClienteForm();
        form.setNome(cliente.getNome());
        form.setCpf(cliente.getCpf());
        form.setEmail(cliente.getEmail());
        form.setTelefone(cliente.getTelefone());
        form.setDataNascimento(cliente.getDataNascimento());
        return form;
    }

    public void aplicarEm(Cliente cliente) {
        cliente.setNome(nome);
        cliente.setCpf(cpf);
        cliente.setEmail(email);
        cliente.setTelefone(telefone);
        cliente.setDataNascimento(dataNascimento);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome == null ? null : nome.trim();
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf == null ? null : cpf.replaceAll("\\D", "");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone == null || telefone.isBlank() ? null : telefone.trim();
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }
}
