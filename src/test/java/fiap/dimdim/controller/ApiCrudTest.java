package fiap.dimdim.controller;

import com.jayway.jsonpath.JsonPath;
import fiap.dimdim.repository.ClienteRepository;
import fiap.dimdim.repository.ContaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD completo pela API REST, nas duas tabelas relacionadas. É a mesma
 * sequência do roteiro de testes do README, executada contra o H2 do perfil
 * de teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiCrudTest {

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
    void crudCompletoDeClienteEConta() throws Exception {
        // INSERT do cliente: CPF com pontuação e e-mail com maiúsculas são normalizados
        MvcResult clienteCriado = mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Ana Souza", "cpf": "123.456.789-09", "email": "Ana.Souza@Exemplo.com",
                                 "telefone": "(11) 91234-5678", "dataNascimento": "1990-05-20"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/clientes/")))
                .andExpect(jsonPath("$.cpf").value("12345678909"))
                .andExpect(jsonPath("$.email").value("ana.souza@exemplo.com"))
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-20"))
                .andReturn();
        long clienteId = idDe(clienteCriado);

        // SELECT do cliente
        mvc.perform(get("/api/clientes/{id}", clienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.quantidadeContas").value(0));

        // UPDATE do cliente
        mvc.perform(put("/api/clientes/{id}", clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Ana Souza Lima", "cpf": "12345678909", "email": "ana.lima@exemplo.com",
                                 "telefone": null, "dataNascimento": "1990-05-20"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza Lima"))
                .andExpect(jsonPath("$.email").value("ana.lima@exemplo.com"));

        // INSERT da conta, relacionada ao cliente
        MvcResult contaCriada = mvc.perform(post("/api/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId": %d, "tipo": "CORRENTE", "saldo": 1500.50, "ativa": true}
                                """.formatted(clienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agencia").value("0001"))
                .andExpect(jsonPath("$.numero").value(matchesPattern("\\d{5}-\\d")))
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.clienteNome").value("Ana Souza Lima"))
                .andReturn();
        long contaId = idDe(contaCriada);

        // SELECT das contas do cliente
        mvc.perform(get("/api/contas").param("clienteId", String.valueOf(clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].saldo").value(1500.5));

        // A FK impede excluir um titular que ainda tem conta
        mvc.perform(delete("/api/clientes/{id}", clienteId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Exclua as contas")));

        // UPDATE da conta
        mvc.perform(put("/api/contas/{id}", contaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId": %d, "tipo": "POUPANCA", "saldo": 2000.00, "ativa": false}
                                """.formatted(clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("POUPANCA"))
                .andExpect(jsonPath("$.ativa").value(false));

        // DELETE da conta e depois do cliente
        mvc.perform(delete("/api/contas/{id}", contaId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/clientes/{id}", clienteId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/clientes/{id}", clienteId)).andExpect(status().isNotFound());
    }

    @Test
    void validacaoDevolve400ComOsCamposInvalidos() throws Exception {
        mvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "", "cpf": "123", "email": "invalido", "dataNascimento": "2999-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..erros[*].campo", hasItem("cpf")))
                .andExpect(jsonPath("$..erros[*].campo", hasItem("nome")))
                .andExpect(jsonPath("$..erros[*].campo", hasItem("email")))
                .andExpect(jsonPath("$..erros[*].campo", hasItem("dataNascimento")));
    }

    @Test
    void cpfDuplicadoDevolve409() throws Exception {
        String corpo = """
                {"nome": "Carlos Dias", "cpf": "11122233344", "email": "carlos@exemplo.com", "dataNascimento": "1980-01-15"}
                """;
        mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.replace("carlos@", "outro@")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um cliente com este CPF."));
    }

    @Test
    void titularDaContaNaoMuda() throws Exception {
        long primeiro = idDe(mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Primeiro", "cpf": "22233344455", "email": "primeiro@exemplo.com", "dataNascimento": "1991-02-02"}
                                """))
                .andReturn());
        long segundo = idDe(mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Segundo", "cpf": "33344455566", "email": "segundo@exemplo.com", "dataNascimento": "1992-03-03"}
                                """))
                .andReturn());
        long conta = idDe(mvc.perform(post("/api/contas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId": %d, "tipo": "SALARIO", "saldo": 10, "ativa": true}
                                """.formatted(primeiro)))
                .andReturn());

        mvc.perform(put("/api/contas/{id}", conta).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId": %d, "tipo": "SALARIO", "saldo": 10, "ativa": true}
                                """.formatted(segundo)))
                .andExpect(status().isConflict());
    }

    @Test
    void recursoInexistenteDevolve404() throws Exception {
        mvc.perform(get("/api/contas/{id}", 987654))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Conta 987654 não encontrada."));
    }

    private static long idDe(MvcResult resultado) throws Exception {
        Number id = JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
