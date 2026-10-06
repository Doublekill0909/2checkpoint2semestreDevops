#!/usr/bin/env bash
# =====================================================================
# ETAPA 4 - App Service: plano Linux + Web App Java SE 21
#
#   1) plano do App Service (Linux)
#   2) Web App com runtime Java SE 21, HTTPS obrigatorio e TLS 1.2
#   3) app settings: conexao com o Azure SQL e Application Insights
#   4) configuracoes gerais (Always On, FTP desligado, HTTP/2)
#   5) logs da aplicacao no filesystem (az webapp log tail / Log stream)
#   6) diagnostic settings da Web App -> Log Analytics workspace
#   7) credencial de publicacao (publish profile) para o GitHub Actions
#
# Aqui nada e implantado: a Web App sobe vazia e o codigo chega pelo
# pipeline do GitHub Actions, disparado no 05-github-actions.sh.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "[1/7] Criando o plano do App Service ${APP_SERVICE_PLAN} (Linux, ${APP_SERVICE_SKU})..."
az appservice plan create \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${APP_SERVICE_PLAN}" \
  --location "${LOCATION}" \
  --is-linux \
  --sku "${APP_SERVICE_SKU}" \
  --tags "${TAGS[@]}" \
  --query "{Plano:name, Sku:sku.name, Regiao:location, SO:kind}" \
  --output table

echo
echo "[2/7] Criando a Web App ${WEBAPP_NAME} (${WEBAPP_RUNTIME})..."
if az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" -o none 2>/dev/null; then
  echo "      Web App ja existe; reaproveitando."
else
  # --basic-auth Enabled: o publish profile usado pelo GitHub Actions
  # autentica no SCM (Kudu) com usuario e senha de publicacao. Apps novos
  # nascem com isso desligado; o FTP e desligado de novo no passo 7.
  az webapp create \
    --resource-group "${RESOURCE_GROUP}" \
    --plan "${APP_SERVICE_PLAN}" \
    --name "${WEBAPP_NAME}" \
    --runtime "${WEBAPP_RUNTIME}" \
    --https-only true \
    --min-tls-version 1.2 \
    --basic-auth Enabled \
    --tags "${TAGS[@]}" \
    --output none
fi
WEBAPP_HOST=$(az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" --query defaultHostName -o tsv)
az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" \
  --query "{WebApp:name, Host:defaultHostName, Runtime:siteConfig.linuxFxVersion, HTTPSOnly:httpsOnly, Estado:state}" \
  -o table

echo
echo "[3/7] Configurando as app settings (banco e Application Insights)..."
SQL_FQDN=$(az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" --query fullyQualifiedDomainName -o tsv)
DB_URL="jdbc:sqlserver://${SQL_FQDN}:1433;database=${SQL_DB};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;"
APPINSIGHTS_CONNECTION_STRING=$(az monitor app-insights component show \
  --app "${APP_INSIGHTS}" -g "${RESOURCE_GROUP}" --query connectionString -o tsv)

# --output none e obrigatorio aqui: por padrao o comando devolve TODAS as
# settings com os valores, o que exibiria a senha do banco no terminal.
#
# ApplicationInsightsAgent_EXTENSION_VERSION=~3 liga a instrumentacao
# automatica do App Service: o agente Java do Application Insights e
# injetado na JVM e coleta requisicoes, chamadas JDBC ao Azure SQL, logs e
# excecoes, sem nenhuma linha de codigo na aplicacao.
az webapp config appsettings set \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${WEBAPP_NAME}" \
  --settings \
    "DB_URL=${DB_URL}" \
    "DB_USER=${APP_DB_USER}" \
    "DB_PASSWORD=${APP_DB_PASSWORD}" \
    "APPLICATIONINSIGHTS_CONNECTION_STRING=${APPINSIGHTS_CONNECTION_STRING}" \
    "ApplicationInsightsAgent_EXTENSION_VERSION=~3" \
    "XDT_MicrosoftApplicationInsights_Mode=recommended" \
    "TZ=America/Sao_Paulo" \
  --output none
echo "      Settings gravadas (valores ocultos). Nomes configurados:"
az webapp config appsettings list -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" --query "[].name" -o tsv \
  | sed 's/^/        - /'

echo
echo "[4/7] Configuracoes gerais da Web App..."
ALWAYS_ON=true
if [[ "${APP_SERVICE_SKU^^}" == F1 || "${APP_SERVICE_SKU^^}" == D1 ]]; then
  ALWAYS_ON=false   # os planos Free e Shared nao oferecem Always On
fi
az webapp config set \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${WEBAPP_NAME}" \
  --always-on "${ALWAYS_ON}" \
  --ftps-state Disabled \
  --http20-enabled true \
  --output none
echo "      Always On=${ALWAYS_ON}, FTP/FTPS desligado, HTTP/2 ligado."

echo
echo "[5/7] Ligando os logs da aplicacao (stdout da JVM)..."
az webapp log config \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${WEBAPP_NAME}" \
  --application-logging filesystem \
  --docker-container-logging filesystem \
  --level information \
  --output none
echo "      Acompanhe com: az webapp log tail -g ${RESOURCE_GROUP} -n ${WEBAPP_NAME}"

echo
echo "[6/7] Enviando os logs da Web App para o Log Analytics workspace..."
WORKSPACE_ID=$(az monitor log-analytics workspace show -g "${RESOURCE_GROUP}" -n "${LOG_ANALYTICS}" --query id -o tsv)
WEBAPP_ID=$(az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" --query id -o tsv)
# Nem toda categoria existe em todo plano; se a lista completa for
# recusada, tenta so as categorias basicas antes de desistir.
CATEGORIAS_COMPLETAS='[{"category":"AppServiceHTTPLogs","enabled":true},{"category":"AppServiceConsoleLogs","enabled":true},{"category":"AppServiceAppLogs","enabled":true},{"category":"AppServicePlatformLogs","enabled":true}]'
CATEGORIAS_BASICAS='[{"category":"AppServiceHTTPLogs","enabled":true},{"category":"AppServiceConsoleLogs","enabled":true}]'
DIAG_OK=""
for categorias in "${CATEGORIAS_COMPLETAS}" "${CATEGORIAS_BASICAS}"; do
  if az monitor diagnostic-settings create \
       --name diag-webapp-para-log-analytics \
       --resource "${WEBAPP_ID}" \
       --workspace "${WORKSPACE_ID}" \
       --logs "${categorias}" \
       --metrics '[{"category":"AllMetrics","enabled":true}]' \
       --output none 2>/dev/null; then
    DIAG_OK="sim"
    break
  fi
done
if [[ -n "${DIAG_OK}" ]]; then
  echo "      Diagnostic setting da Web App criado (tabelas AppServiceHTTPLogs, AppServiceConsoleLogs...)."
else
  echo "      AVISO: nao foi possivel criar o diagnostic setting da Web App; seguindo."
fi

echo
echo "[7/7] Ajustando as credenciais de publicacao..."
# SCM (Kudu) com autenticacao basica ligada: e por ele que o publish
# profile do GitHub Actions faz o deploy. FTP desligado: ninguem publica
# arquivos fora do pipeline.
az resource update --resource-group "${RESOURCE_GROUP}" \
  --namespace Microsoft.Web --resource-type basicPublishingCredentialsPolicies \
  --parent "sites/${WEBAPP_NAME}" --name scm --set properties.allow=true --output none
az resource update --resource-group "${RESOURCE_GROUP}" \
  --namespace Microsoft.Web --resource-type basicPublishingCredentialsPolicies \
  --parent "sites/${WEBAPP_NAME}" --name ftp --set properties.allow=false --output none
echo "      SCM liberado para o deploy; FTP bloqueado."

echo
echo "====================================================="
echo " Web App criada: https://${WEBAPP_HOST}"
echo " Ainda sem codigo: o deploy e feito pelo GitHub Actions."
echo " Proximo passo: bash scripts/05-github-actions.sh"
echo "====================================================="
