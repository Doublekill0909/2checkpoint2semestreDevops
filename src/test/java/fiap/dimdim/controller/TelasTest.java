package fiap.dimdim.controller;

import fiap.dimdim.entity.Cliente;
import fiap.dimdim.entity.Conta;
import fiap.dimdim.repository.ClienteRepository;
import fiap.dimdim.repository.ContaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * O front end Thymeleaf, renderizado de verdade: cada tela abre sem erro de
 * template e o CRUD das duas tabelas funciona pelos formulários.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TelasTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private ContaRepository contas;

    @BeforeEach
    void limparBanco() {
        contas.deleteAll();
        clientes.deleteAll();
    }

    @Test
    void telasVaziasAbremComOrientacao() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("O DimDim guarda")))
                .andExpect(content().string(containsString("Nenhuma conta aberta ainda")))
                .andExpect(content().string(containsString("Execução local")));
        mvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhum cliente cadastrado ainda")));
        mvc.perform(get("/clientes/novo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Data de nascimento")));
        mvc.perform(get("/contas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma conta encontrada")));
        mvc.perform(get("/contas/nova"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cadastre um cliente")));
    }

    @Test
    void crudCompletoPelosFormularios() throws Exception {
        // INSERT do cliente
        mvc.perform(post("/clientes")
                        .param("nome", "Bruno Lima")
                        .param("cpf", "987.654.321-00")
                        .param("email", "bruno@exemplo.com")
                        .param("telefone", "")
                        .param("dataNascimento", "1985-03-10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("sucesso"));
        Cliente cliente = clientes.findAll().getFirst();
        assertThat(cliente.getCpf()).isEqualTo("98765432100");
        assertThat(cliente.getTelefone()).isNull();
        assertThat(cliente.getDataCadastro()).isNotNull();

        mvc.perform(get("/clientes/{id}", cliente.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bruno Lima")))
                .andExpect(content().string(containsString("987.654.321-00")))
                .andExpect(content().string(containsString("Este cliente ainda não tem contas")));

        // UPDATE do cliente
        mvc.perform(get("/clientes/{id}/editar", cliente.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Salvar alterações")));
        mvc.perform(post("/clientes/{id}", cliente.getId())
                        .param("nome", "Bruno Lima Souza")
                        .param("cpf", "98765432100")
                        .param("email", "bruno@exemplo.com")
                        .param("telefone", "(21) 99876-5432")
                        .param("dataNascimento", "1985-03-10"))
                .andExpect(redirectedUrl("/clientes/" + cliente.getId()));
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getNome()).isEqualTo("Bruno Lima Souza");

        // INSERT da conta
        mvc.perform(get("/contas/nova").param("clienteId", String.valueOf(cliente.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bruno Lima Souza")));
        mvc.perform(post("/contas")
                        .param("clienteId", String.valueOf(cliente.getId()))
                        .param("tipo", "POUPANCA")
                        .param("saldo", "250.00")
                        .param("ativa", "true")
                        .param("_ativa", "on"))
                .andExpect(redirectedUrl("/contas"))
                .andExpect(flash().attributeExists("sucesso"));
        Conta conta = contas.findAll().getFirst();
        assertThat(conta.getNumero()).matches("\\d{5}-\\d");

        mvc.perform(get("/contas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conta poupança")))
                .andExpect(content().string(containsString(conta.getNumero())));
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 conta ativa")));

        // UPDATE da conta: a caixa "ativa" desmarcada chega só como _ativa
        mvc.perform(get("/contas/{id}/editar", conta.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(conta.getNumero())));
        mvc.perform(post("/contas/{id}", conta.getId())
                        .param("clienteId", String.valueOf(cliente.getId()))
                        .param("tipo", "CORRENTE")
                        .param("saldo", "300.00")
                        .param("_ativa", "on"))
                .andExpect(redirectedUrl("/contas"));
        Conta atualizada = contas.findById(conta.getId()).orElseThrow();
        assertThat(atualizada.isAtiva()).isFalse();
        assertThat(atualizada.getSaldo()).isEqualByComparingTo(new BigDecimal("300.00"));

        // DELETE bloqueado: o cliente ainda é titular de uma conta
        mvc.perform(post("/clientes/{id}/excluir", cliente.getId()))
                .andExpect(redirectedUrl("/clientes/" + cliente.getId()))
                .andExpect(flash().attributeExists("erro"));

        // DELETE da conta e do cliente
        mvc.perform(post("/contas/{id}/excluir", conta.getId()))
                .andExpect(redirectedUrl("/contas"));
        mvc.perform(post("/clientes/{id}/excluir", cliente.getId()))
                .andExpect(redirectedUrl("/clientes"));
        assertThat(clientes.count()).isZero();
        assertThat(contas.count()).isZero();
    }

    @Test
    void formularioInvalidoVoltaComAsMensagens() throws Exception {
        mvc.perform(post("/clientes")
                        .param("nome", "")
                        .param("cpf", "123")
                        .param("email", "invalido")
                        .param("dataNascimento", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/form"))
                .andExpect(model().attributeHasFieldErrors("cliente", "nome", "cpf", "email", "dataNascimento"))
                .andExpect(content().string(containsString("O CPF deve ter 11 dígitos.")));
    }

    @Test
    void cpfDuplicadoApareceNoFormulario() throws Exception {
        mvc.perform(post("/clientes")
                .param("nome", "Carla Nunes")
                .param("cpf", "55566677788")
                .param("email", "carla@exemplo.com")
                .param("dataNascimento", "1995-07-07"));
        mvc.perform(post("/clientes")
                        .param("nome", "Outra Pessoa")
                        .param("cpf", "555.666.777-88")
                        .param("email", "outra@exemplo.com")
                        .param("dataNascimento", "1990-01-01"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("cliente", "cpf"))
                .andExpect(content().string(containsString("Já existe um cliente com este CPF.")));
    }

    @Test
    void clienteInexistenteMostraPagina404() throws Exception {
        mvc.perform(get("/clientes/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Cliente 999999 não encontrado.")));
    }
}
