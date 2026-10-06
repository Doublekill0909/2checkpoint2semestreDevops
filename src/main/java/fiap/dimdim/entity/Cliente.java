package fiap.dimdim.entity;

import fiap.dimdim.config.Datas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cliente do banco, titular de uma ou mais contas (tabela tb_cliente).
 *
 * O mapeamento espelha coluna a coluna o scripts/script_bd.sql. A aplicação
 * sobe com ddl-auto=validate, de modo que o Hibernate recusa iniciar se o
 * banco divergir deste mapeamento.
 */
@Entity
@Table(name = "tb_cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /** Somente os 11 dígitos, sem pontuação. */
    @Column(name = "cpf", nullable = false, length = 11)
    private String cpf;

    /** Sempre em minúsculas, para que a unicidade não dependa da caixa. */
    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @PrePersist
    void antesDeInserir() {
        if (dataCadastro == null) {
            dataCadastro = Datas.agora();
        }
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }
}
