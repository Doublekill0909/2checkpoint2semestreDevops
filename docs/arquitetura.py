"""
Gera docs/arquitetura.png, o desenho macro da arquitetura do DimDim.

Usa a biblioteca "diagrams" (https://diagrams.mingrammer.com), que desenha
a infraestrutura com os icones oficiais da Azure e do GitHub a partir de
codigo. Para regerar a imagem depois de mudar a arquitetura:

    pip install diagrams      # requer o Graphviz instalado (comando dot)
    cd docs && python arquitetura.py
"""
from diagrams import Cluster, Diagram, Edge
from diagrams.azure.appservices import AppServicePlans, AppServices
from diagrams.azure.databases import SQLDatabase
from diagrams.azure.monitor import ApplicationInsights, LogAnalyticsWorkspaces
from diagrams.azure.other import AzureCloudShell
from diagrams.onprem.ci import GithubActions
from diagrams.onprem.client import Users
from diagrams.onprem.vcs import Github

FONTE = "DejaVu Sans"

grafo = {
    "fontname": FONTE,
    "fontsize": "22",
    "labelloc": "t",
    "pad": "0.6",
    "nodesep": "0.9",
    "ranksep": "1.6",
    "splines": "spline",
}
no = {"fontname": FONTE, "fontsize": "12"}
seta = {"fontname": FONTE, "fontsize": "11", "color": "#4b5466", "fontcolor": "#1f2430"}


def cluster(cor_fundo, cor_borda):
    return {
        "fontname": FONTE,
        "fontsize": "14",
        "bgcolor": cor_fundo,
        "pencolor": cor_borda,
        "penwidth": "1.5",
        "style": "rounded",
        "margin": "22",
    }


with Diagram(
    "DimDim: arquitetura da solução na Azure",
    filename="arquitetura",
    outformat="png",
    direction="LR",
    show=False,
    graph_attr=grafo,
    node_attr=no,
    edge_attr=seta,
):
    usuarios = Users("Usuários do banco\n(navegador)")

    with Cluster("GitHub", graph_attr=cluster("#f6f8fa", "#8c959f")):
        repositorio = Github("Repositório\n2checkpoint2semestreDevops")
        pipeline = GithubActions("GitHub Actions\nbuild + testes + deploy")

    shell = AzureCloudShell("Azure Cloud Shell\naz CLI + sqlcmd + gh\n(scripts/01 a 07)")

    with Cluster("Assinatura Azure for Students", graph_attr=cluster("#f3f8fd", "#0078d4")):
        with Cluster("Resource Group rg-rm566234-dimdim (chilecentral)", graph_attr=cluster("#ffffff", "#5e9cd6")):
            with Cluster("App Service Plan B1 (Linux)", graph_attr=cluster("#fffbe6", "#d4a600")):
                plano = AppServicePlans("plan-rm566234-dimdim")
                webapp = AppServices("Web App rm566234-dimdim\nJava SE 21, Spring Boot 4\nThymeleaf + API REST")

            with Cluster("Azure SQL (PaaS)", graph_attr=cluster("#eef7ee", "#3a8f3a")):
                banco = SQLDatabase("sql-rm566234-dimdim\nbanco db-dimdim (Basic)\ntb_cliente 1:N tb_conta")

            with Cluster("Monitoramento", graph_attr=cluster("#f5f0fb", "#8a5cc7")):
                appinsights = ApplicationInsights("Application Insights\nappi-rm566234-dimdim")
                workspace = LogAnalyticsWorkspaces("Log Analytics\nlog-rm566234-dimdim")

    plano - Edge(style="dotted", color="#d4a600", arrowhead="none") - webapp

    usuarios >> Edge(label="HTTPS (TLS 1.2)", color="#1f2430", penwidth="2") >> webapp
    webapp >> Edge(label="JDBC 1433 com TLS\nusuário da aplicação\n(leitura e escrita)", color="#3a8f3a", penwidth="2") >> banco
    webapp >> Edge(label="agente Java: requisições,\nchamadas SQL, logs, exceções", color="#8a5cc7") >> appinsights
    appinsights >> Edge(label="telemetria", color="#8a5cc7") >> workspace
    banco >> Edge(xlabel="métricas e logs do banco\n(diagnostic settings)", style="dashed", color="#8a5cc7") >> workspace

    repositorio >> Edge(label="push na main", color="#57606a") >> pipeline
    pipeline >> Edge(label="deploy do JAR (OneDeploy)\ncom publish profile", color="#1f2430", penwidth="2") >> webapp

    shell >> Edge(label="az CLI: cria todos\nos recursos", style="dashed", color="#0078d4") >> plano
    shell >> Edge(label="sqlcmd: DDL, usuário\ne SELECT de evidência", style="dashed", color="#3a8f3a") >> banco
    shell >> Edge(label="gh: secret e variable\ndo deploy, disparo\ndo workflow", style="dashed", color="#57606a") >> repositorio
