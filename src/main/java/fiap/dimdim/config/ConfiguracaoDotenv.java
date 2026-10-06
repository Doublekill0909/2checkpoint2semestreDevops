package fiap.dimdim.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Carrega o arquivo {@code .env} da raiz do projeto como fonte de configuração.
 *
 * O Spring Boot não lê arquivos {@code .env} por conta própria. Sem isto, a
 * aplicação só sobe localmente quando alguém exporta {@code DB_URL},
 * {@code DB_USER} e {@code DB_PASSWORD} antes, o que quebra a execução pela IDE
 * e por {@code ./mvnw spring-boot:run}.
 *
 * No App Service o {@code .env} não existe: as mesmas chaves chegam como app
 * settings, criadas pelo scripts/04-webapp.sh, e este carregador não faz nada.
 *
 * Roda como {@link EnvironmentPostProcessor}, antes de qualquer resolução de
 * placeholder, e é registrado em {@code META-INF/spring.factories}.
 */
public class ConfiguracaoDotenv implements EnvironmentPostProcessor {

    private static final String NOME_DA_FONTE = "arquivo .env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        carregar(environment, Paths.get(".env"));
    }

    /**
     * Adiciona o conteúdo do arquivo com a <b>menor</b> precedência do ambiente.
     *
     * Na nuvem a configuração vem das variáveis reais. Se o arquivo vencesse, um
     * {@code .env} esquecido na máquina sobrescreveria silenciosamente a
     * configuração de verdade.
     */
    void carregar(ConfigurableEnvironment environment, Path arquivo) {
        if (!Files.isRegularFile(arquivo)) {
            return;
        }
        Map<String, Object> valores = ler(arquivo);
        if (!valores.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(NOME_DA_FONTE, valores));
        }
    }

    private Map<String, Object> ler(Path arquivo) {
        Map<String, Object> valores = new HashMap<>();
        try {
            for (String linha : Files.readAllLines(arquivo)) {
                acrescentar(valores, linha.trim());
            }
        } catch (IOException e) {
            // Configuração ilegível não deve derrubar o boot: as variáveis de
            // ambiente reais ainda podem suprir tudo o que a aplicação precisa.
            System.err.println("Não foi possível ler " + arquivo + ": " + e.getMessage());
        }
        return valores;
    }

    private void acrescentar(Map<String, Object> valores, String linha) {
        if (linha.isEmpty() || linha.startsWith("#")) {
            return;
        }
        // Limite de 2: o valor pode conter '=', como na URL JDBC do Azure SQL.
        String[] partes = linha.split("=", 2);
        if (partes.length != 2) {
            return;
        }
        String chave = partes[0].trim();
        String valor = tirarAspas(partes[1].trim());
        if (!chave.isEmpty() && !valor.isEmpty()) {
            valores.put(chave, valor);
        }
    }

    /**
     * O mesmo .env é lido pelo bash dos scripts da Azure, onde valores com ';'
     * (a URL JDBC) precisam de aspas. Aqui as aspas externas são descartadas
     * para que os dois leitores enxerguem o mesmo valor.
     */
    private String tirarAspas(String valor) {
        if (valor.length() >= 2) {
            char primeiro = valor.charAt(0);
            char ultimo = valor.charAt(valor.length() - 1);
            if ((primeiro == '"' || primeiro == '\'') && primeiro == ultimo) {
                return valor.substring(1, valor.length() - 1);
            }
        }
        return valor;
    }
}
