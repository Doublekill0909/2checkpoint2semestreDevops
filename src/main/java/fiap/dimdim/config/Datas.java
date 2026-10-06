package fiap.dimdim.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Relógio da aplicação, sempre no horário de Brasília.
 *
 * O App Service e o Azure SQL Database rodam em UTC. Se a data de cadastro
 * fosse gerada com o fuso padrão da JVM, um cliente cadastrado às 21h em São
 * Paulo apareceria com meia-noite no SELECT do banco. Fixar o fuso aqui torna
 * o valor gravado independente da configuração do servidor, e o DDL usa o
 * mesmo fuso nos valores DEFAULT (AT TIME ZONE 'E. South America Standard Time').
 */
public final class Datas {

    public static final ZoneId FUSO_BRASILIA = ZoneId.of("America/Sao_Paulo");

    private Datas() {
    }

    /** Data e hora atuais em Brasília, truncadas no segundo, como a coluna DATETIME2(0). */
    public static LocalDateTime agora() {
        return LocalDateTime.now(FUSO_BRASILIA).truncatedTo(ChronoUnit.SECONDS);
    }

    public static LocalDate hoje() {
        return LocalDate.now(FUSO_BRASILIA);
    }
}
