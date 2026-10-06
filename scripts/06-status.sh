#!/usr/bin/env bash
# =====================================================================
# Panorama de tudo o que foi criado na Azure e no GitHub.
#
# Bom para abrir o video: lista numa tela so o Resource Group, a Web App,
# o Azure SQL, o monitoramento e as ultimas execucoes do pipeline. Nenhum
# valor secreto e exibido (das app settings aparecem so os nomes).
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "#####################################################"
echo "# 1) RECURSOS DO RESOURCE GROUP ${RESOURCE_GROUP}"
echo "#####################################################"
az resource list --resource-group "${RESOURCE_GROUP}" \
  --query "[].{Nome:name, Tipo:type, Regiao:location}" --output table

echo
echo "#####################################################"
echo "# 2) APP SERVICE (PaaS, Java SE 21)"
echo "#####################################################"
az appservice plan show -g "${RESOURCE_GROUP}" -n "${APP_SERVICE_PLAN}" \
  --query "{Plano:name, Sku:sku.name, Instancias:sku.capacity, SO:kind}" -o table
az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" \
  --query "{WebApp:name, Estado:state, Host:defaultHostName, Runtime:siteConfig.linuxFxVersion, HTTPSOnly:httpsOnly}" \
  -o table
echo
echo "-- app settings (somente os nomes):"
az webapp config appsettings list -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" --query "[].name" -o tsv \
  | sed 's/^/   - /'

echo
echo "#####################################################"
echo "# 3) AZURE SQL DATABASE (PaaS)"
echo "#####################################################"
az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" \
  --query "{Servidor:name, FQDN:fullyQualifiedDomainName, TLS:minimalTlsVersion, AcessoPublico:publicNetworkAccess}" -o table
az sql db show -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" -n "${SQL_DB}" \
  --query "{Banco:name, Tier:currentServiceObjectiveName, Status:status, Regiao:location}" -o table
echo
echo "-- regras de firewall:"
az sql server firewall-rule list -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" \
  --query "[].{Regra:name, Inicio:startIpAddress, Fim:endIpAddress}" -o table

echo
echo "#####################################################"
echo "# 4) MONITORAMENTO"
echo "#####################################################"
az monitor app-insights component show --app "${APP_INSIGHTS}" -g "${RESOURCE_GROUP}" \
  --query "{AppInsights:name, Tipo:applicationType, Workspace:workspaceResourceId}" -o table 2>/dev/null \
  || echo "   (instale a extensao: az extension add --name application-insights)"
az monitor diagnostic-settings list \
  --resource "$(az sql db show -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" -n "${SQL_DB}" --query id -o tsv)" \
  --query "[].{DiagnosticoDoBanco:name}" -o table 2>/dev/null || true

echo
echo "#####################################################"
echo "# 5) PIPELINE NO GITHUB ACTIONS"
echo "#####################################################"
if command -v gh >/dev/null 2>&1 && gh auth status --hostname github.com >/dev/null 2>&1; then
  gh run list --repo "${GITHUB_REPO}" --workflow "${GITHUB_WORKFLOW}" --limit 5
else
  echo "   (gh nao autenticado; veja https://github.com/${GITHUB_REPO}/actions)"
fi

WEBAPP_HOST=$(az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" --query defaultHostName -o tsv)
SAUDE=$(curl -s --max-time 20 "https://${WEBAPP_HOST}/actuator/health" || echo "sem resposta")

echo
echo "====================================================="
echo " Aplicacao ....: https://${WEBAPP_HOST}"
echo " Health .......: ${SAUDE}"
echo " Banco ........: $(az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" --query fullyQualifiedDomainName -o tsv) / ${SQL_DB}"
echo " Evidencias CRUD no banco: bash scripts/07-consultar-banco.sh"
echo "====================================================="
