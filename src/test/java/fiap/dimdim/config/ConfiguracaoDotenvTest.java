package fiap.dimdim.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConfiguracaoDotenvTest {

    private final ConfiguracaoDotenv carregador = new ConfiguracaoDotenv();

    private Path escreverEnv(Path pasta, String conteudo) throws IOException {
        Path arquivo = pasta.resolve(".env");
        Files.writeString(arquivo, conteudo);
        return arquivo;
    }

    @Test
    void carregaVariavelDoArquivo(@TempDir Path pasta) throws IOException {
        Path env = escreverEnv(pasta, "DB_USER=usuario_teste\n");
        var environment = new StandardEnvironment();

        carregador.carregar(environment, env);

        assertThat(environment.getProperty("DB_USER")).isEqualTo("usuario_teste");
    }

    @Test
    void ignoraComentariosLinhasEmBrancoEEspacos(@TempDir Path pasta) throws IOException {
        Path env = escreverEnv(pasta, """
                # comentario no topo

                DB_USER = usuario_teste

                # outro comentario
                DB_PASSWORD=senha
                """);
        var environment = new StandardEnvironment();

        carregador.carregar(environment, env);

        assertThat(environment.getProperty("DB_USER")).isEqualTo("usuario_teste");
        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("senha");
    }

    /**
     * A URL JDBC do Azure SQL tem ';' e '=', e no .env ela precisa de aspas
     * para que o bash dos scripts a leia inteira. O valor que chega ao
     * Spring deve ser a URL sem as aspas.
     */
    @Test
    void removeAspasExternasEPreservaIgualNoValor(@TempDir Path pasta) throws IOException {
        Path env = escreverEnv(pasta,
                "DB_URL=\"jdbc:sqlserver://srv.database.windows.net:1433;database=db;encrypt=true\"\n");
        var environment = new StandardEnvironment();

        carregador.carregar(environment, env);

        assertThat(environment.getProperty("DB_URL"))
                .isEqualTo("jdbc:sqlserver://srv.database.windows.net:1433;database=db;encrypt=true");
    }

    /**
     * Na nuvem a configuração vem do ambiente (app settings). Se o arquivo
     * vencesse, um .env esquecido sobrescreveria a configuração real, por isso
     * ele entra com a menor precedência.
     */
    @Test
    void ambienteRealVenceOArquivo(@TempDir Path pasta) throws IOException {
        Path env = escreverEnv(pasta, "DB_PASSWORD=valor-do-arquivo\n");
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("ambiente-real", Map.of("DB_PASSWORD", "valor-do-ambiente")));

        carregador.carregar(environment, env);

        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("valor-do-ambiente");
    }

    @Test
    void semArquivoNaoQuebra(@TempDir Path pasta) {
        var environment = new StandardEnvironment();

        assertThatCode(() -> carregador.carregar(environment, pasta.resolve(".env")))
                .doesNotThrowAnyException();
    }
}
