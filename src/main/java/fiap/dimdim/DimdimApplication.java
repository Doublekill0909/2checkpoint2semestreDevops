package fiap.dimdim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DimDim: cadastro de clientes e de suas contas bancárias.
 *
 * A aplicação roda no Azure App Service (Java SE 21, Linux) e persiste os
 * dados no Azure SQL Database. A telemetria (requisições, chamadas JDBC ao
 * banco, logs e exceções) é coletada pelo agente Java do Application
 * Insights, que o próprio App Service injeta quando o script
 * scripts/04-webapp.sh liga a instrumentação automática. Por isso não há
 * nenhum código de monitoramento aqui.
 */
@SpringBootApplication
public class DimdimApplication {

	public static void main(String[] args) {
		SpringApplication.run(DimdimApplication.class, args);
	}

}
