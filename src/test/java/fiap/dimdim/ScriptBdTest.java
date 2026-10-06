package fiap.dimdim;

import fiap.dimdim.entity.Cliente;
import fiap.dimdim.entity.Conta;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato entre o scripts/script_bd.sql e as entidades JPA.
 *
 * Na nuvem a aplicação sobe com ddl-auto=validate contra o schema criado por
 * esse script. Este teste lê o DDL e confere, para cada coluna mapeada, se ela
 * existe na tabela certa, com o tipo do SQL Server correspondente ao tipo Java
 * e com a mesma nulidade. Assim uma divergência quebra o build no GitHub
 * Actions, antes do deploy, e não a subida no App Service.
 */
class ScriptBdTest {

    private static final Path SCRIPT = Path.of("scripts", "script_bd.sql");

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {Cliente.class, Conta.class})
    void ddlTemExatamenteAsColunasDaEntidade(Class<?> entidade) throws IOException {
        String tabela = entidade.getAnnotation(Table.class).name();
        Map<String, String> colunasDoDdl = colunasDaTabela(Files.readString(SCRIPT), tabela);
        Set<String> colunasMapeadas = new LinkedHashSet<>();

        for (Field campo : entidade.getDeclaredFields()) {
            Column coluna = campo.getAnnotation(Column.class);
            JoinColumn juncao = campo.getAnnotation(JoinColumn.class);
            if (coluna == null && juncao == null) {
                continue;
            }
            String nome = coluna != null ? coluna.name() : juncao.name();
            boolean obrigatoria = campo.isAnnotationPresent(Id.class)
                    || (coluna != null ? !coluna.nullable() : !juncao.nullable());
            colunasMapeadas.add(nome);

            assertThat(colunasDoDdl)
                    .as("a coluna %s.%s existe no script_bd.sql", tabela, nome)
                    .containsKey(nome);
            String definicao = colunasDoDdl.get(nome);
            assertThat(definicao)
                    .as("tipo de %s.%s", tabela, nome)
                    .startsWith(tipoSqlServer(campo, coluna));
            if (obrigatoria) {
                assertThat(definicao).as("%s.%s é NOT NULL", tabela, nome).contains("NOT NULL");
            } else {
                assertThat(definicao).as("%s.%s aceita NULL", tabela, nome).doesNotContain("NOT NULL");
            }
        }

        assertThat(colunasDoDdl.keySet())
                .as("colunas de %s no DDL e na entidade", tabela)
                .containsExactlyInAnyOrderElementsOf(colunasMapeadas);
    }

    /** Tipo esperado no DDL para o tipo Java do campo, como o SQLServerDialect o mapeia. */
    private static String tipoSqlServer(Field campo, Column coluna) {
        Class<?> tipo = campo.getType();
        if (tipo == Long.class || tipo == long.class || campo.isAnnotationPresent(JoinColumn.class)) {
            return "BIGINT";
        }
        if (tipo == String.class || tipo.isEnum()) {
            return "NVARCHAR(" + coluna.length() + ")";
        }
        if (tipo == BigDecimal.class) {
            return "DECIMAL(" + coluna.precision() + "," + coluna.scale() + ")";
        }
        if (tipo == boolean.class || tipo == Boolean.class) {
            return "BIT";
        }
        if (tipo == LocalDate.class) {
            return "DATE";
        }
        if (tipo == LocalDateTime.class) {
            return "DATETIME2";
        }
        throw new IllegalStateException("Tipo sem regra no teste: " + tipo);
    }

    /**
     * Extrai as colunas do bloco CREATE TABLE dbo.<tabela> ( ... ); devolvendo
     * nome da coluna -> restante da definição, em maiúsculas.
     */
    private static Map<String, String> colunasDaTabela(String ddl, String tabela) {
        String inicio = "CREATE TABLE dbo." + tabela + " (";
        int posicao = ddl.indexOf(inicio);
        assertThat(posicao).as("o script_bd.sql cria a tabela %s", tabela).isGreaterThanOrEqualTo(0);

        Map<String, String> colunas = new LinkedHashMap<>();
        String[] linhas = ddl.substring(posicao + inicio.length()).split("\\R");
        for (String bruta : linhas) {
            String linha = bruta.strip();
            if (linha.startsWith(");")) {
                break;
            }
            if (linha.isEmpty() || linha.startsWith("CONSTRAINT") || linha.startsWith("ON ") || linha.startsWith("--")) {
                continue;
            }
            String[] partes = linha.split("\\s+", 2);
            String definicao = partes.length > 1 ? partes[1] : "";
            if (definicao.endsWith(",")) {
                definicao = definicao.substring(0, definicao.length() - 1);
            }
            colunas.put(partes[0].toLowerCase(Locale.ROOT), definicao.toUpperCase(Locale.ROOT));
        }
        return colunas;
    }
}
