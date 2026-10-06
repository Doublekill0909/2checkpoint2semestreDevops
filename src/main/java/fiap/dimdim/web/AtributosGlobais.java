package fiap.dimdim.web;

import fiap.dimdim.enums.TipoConta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Atributos disponíveis em todas as telas.
 *
 * O rodapé mostra em qual host e região do App Service a página foi gerada.
 * WEBSITE_HOSTNAME e REGION_NAME são variáveis que o próprio App Service
 * define; fora dele, o rodapé indica execução local. Isso deixa explícito no
 * vídeo que a aplicação demonstrada é a que está na nuvem.
 */
@ControllerAdvice(basePackages = "fiap.dimdim.controller.web")
public class AtributosGlobais {

    private final Ambiente ambiente;

    public AtributosGlobais(@Value("${WEBSITE_HOSTNAME:}") String host,
                            @Value("${REGION_NAME:}") String regiao) {
        this.ambiente = new Ambiente(host, regiao);
    }

    @ModelAttribute("ambiente")
    public Ambiente ambiente() {
        return ambiente;
    }

    @ModelAttribute("tiposDeConta")
    public TipoConta[] tiposDeConta() {
        return TipoConta.values();
    }

    public record Ambiente(String host, String regiao) {

        public boolean naNuvem() {
            return host != null && !host.isBlank();
        }

        public String descricao() {
            if (!naNuvem()) {
                return "Execução local, fora do Azure App Service.";
            }
            String onde = regiao == null || regiao.isBlank() ? "" : ", região " + regiao;
            return "Página gerada pelo Azure App Service em " + host + onde
                    + ", com dados do Azure SQL Database.";
        }
    }
}
