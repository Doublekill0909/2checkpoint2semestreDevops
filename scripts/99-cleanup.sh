#!/usr/bin/env bash
# =====================================================================
# LIMPEZA - apaga TODO o Resource Group do checkpoint.
#
# Rode isto depois de gravar o video, para nao consumir credito da
# subscription. A operacao e IRREVERSIVEL: leva junto a Web App, o plano,
# o servidor e o banco Azure SQL (com os dados), o Application Insights e
# o Log Analytics workspace.
#
# Tambem remove do repositorio o secret e a variable do deploy; sem eles,
# os proximos pushes na main continuam rodando build e testes, mas pulam o
# deploy em vez de falhar tentando publicar numa Web App que nao existe.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "Isto vai APAGAR o Resource Group ${RESOURCE_GROUP} e tudo dentro dele:"
az resource list \
  --resource-group "${RESOURCE_GROUP}" \
  --query "[].{Nome:name, Tipo:type}" \
  --output table

echo
read -r -p "Digite o nome do Resource Group para confirmar: " CONFIRMACAO

if [[ "${CONFIRMACAO}" != "${RESOURCE_GROUP}" ]]; then
  echo "Nome nao confere. Nada foi apagado."
  exit 1
fi

echo
echo "Apagando ${RESOURCE_GROUP} (roda em segundo plano na Azure)..."
az group delete --name "${RESOURCE_GROUP}" --yes --no-wait

if command -v gh >/dev/null 2>&1 && gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "Removendo o secret e a variable de deploy de ${GITHUB_REPO}..."
  gh secret delete AZURE_WEBAPP_PUBLISH_PROFILE --repo "${GITHUB_REPO}" 2>/dev/null || true
  gh variable delete AZURE_WEBAPP_NAME --repo "${GITHUB_REPO}" 2>/dev/null || true
fi

echo
echo "Solicitacao enviada. Acompanhe com:"
echo "  az group show --name ${RESOURCE_GROUP} --query properties.provisioningState -o tsv"
