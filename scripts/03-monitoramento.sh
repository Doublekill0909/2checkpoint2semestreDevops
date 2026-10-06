#!/usr/bin/env bash
# =====================================================================
# ETAPA 3 - Monitoramento: Log Analytics + Application Insights
#
#   1) Log Analytics workspace, onde toda a telemetria e guardada
#   2) Application Insights baseado nesse workspace. A Web App so recebe
#      a connection string no 04-webapp.sh, que liga o agente Java.
#   3) Diagnostic settings do Azure SQL Database -> workspace: metricas
#      (DTU, conexoes, deadlocks) e logs do banco consultaveis com KQL.
#
# Com isso o monitoramento cobre os dois lados: o Application Insights ve
# cada chamada JDBC que a aplicacao faz (dependencias do tipo SQL), e o
# workspace recebe as metricas do proprio banco.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "[1/4] Instalando/atualizando a extensao application-insights do az CLI..."
az extension add --name application-insights --upgrade --yes --only-show-errors

echo
echo "[2/4] Criando o Log Analytics workspace ${LOG_ANALYTICS}..."
az monitor log-analytics workspace create \
  --resource-group "${RESOURCE_GROUP}" \
  --workspace-name "${LOG_ANALYTICS}" \
  --location "${LOCATION}" \
  --retention-time 30 \
  --tags "${TAGS[@]}" \
  --query "{Workspace:name, Regiao:location, RetencaoDias:retentionInDays}" \
  --output table
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  -g "${RESOURCE_GROUP}" -n "${LOG_ANALYTICS}" --query id -o tsv)

echo
echo "[3/4] Criando o Application Insights ${APP_INSIGHTS} (baseado no workspace)..."
az monitor app-insights component create \
  --app "${APP_INSIGHTS}" \
  --resource-group "${RESOURCE_GROUP}" \
  --location "${LOCATION}" \
  --kind web \
  --application-type web \
  --workspace "${WORKSPACE_ID}" \
  --tags "${TAGS[@]}" \
  --query "{AppInsights:name, Regiao:location, Tipo:applicationType, Estado:provisioningState}" \
  --output table

echo
echo "[4/4] Enviando metricas e logs do Azure SQL Database para o workspace..."
DB_ID=$(az sql db show -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" -n "${SQL_DB}" --query id -o tsv)
# Primeiro tenta metricas + logs; se o tier do banco recusar alguma
# categoria de log, fica so com as metricas.
if az monitor diagnostic-settings create \
     --name diag-sql-para-log-analytics \
     --resource "${DB_ID}" \
     --workspace "${WORKSPACE_ID}" \
     --metrics '[{"category":"Basic","enabled":true},{"category":"InstanceAndAppAdvanced","enabled":true}]' \
     --logs '[{"categoryGroup":"allLogs","enabled":true}]' \
     --output none 2>/dev/null; then
  echo "      Diagnostic setting do banco criado (tabelas AzureMetrics e AzureDiagnostics)."
elif az monitor diagnostic-settings create \
     --name diag-sql-para-log-analytics \
     --resource "${DB_ID}" \
     --workspace "${WORKSPACE_ID}" \
     --metrics '[{"category":"Basic","enabled":true}]' \
     --output none; then
  echo "      Diagnostic setting do banco criado so com metricas (tabela AzureMetrics)."
else
  # Nao e bloqueante: as metricas basicas do banco continuam no portal.
  echo "      AVISO: nao foi possivel criar o diagnostic setting do banco; seguindo."
fi

echo
echo "====================================================="
echo " Application Insights: ${APP_INSIGHTS}"
echo " Log Analytics ......: ${LOG_ANALYTICS}"
echo " A connection string e entregue a Web App no proximo passo, sem"
echo " passar pelo terminal nem pelo codigo-fonte."
echo " Proximo passo: bash scripts/04-webapp.sh"
echo "====================================================="
