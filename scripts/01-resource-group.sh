#!/usr/bin/env bash
# =====================================================================
# ETAPA 1 - Conferencias da subscription + Resource Group
#
# Antes de criar qualquer recurso, confere quatro coisas que costumam
# derrubar o provisionamento numa subscription Azure for Students:
#   1) se o az CLI esta autenticado (no Cloud Shell ja esta);
#   2) se a regiao escolhida e permitida pela politica da subscription;
#   3) se os provedores de recursos usados estao registrados;
#   4) se a regiao oferece todos os servicos usados.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "[1/5] Conferindo a subscription ativa..."
if ! az account show -o none 2>/dev/null; then
  echo "ERRO: o az CLI nao esta autenticado. Rode 'az login' e repita."
  exit 1
fi
az account show --query "{Subscription:name, Id:id, Usuario:user.name}" -o table

echo
echo "[2/5] Conferindo se a regiao ${LOCATION} e permitida na subscription..."
# Subscriptions de estudante herdam a politica "Allowed resource deployment
# regions", que aceita so algumas regioes (cada aluno tem a sua lista). Se
# a regiao estiver fora dela, a criacao falha com RequestDisallowedByPolicy
# so depois de varios minutos -- melhor descobrir agora.
PERMITIDAS=$(az policy assignment list --disable-scope-strict-match \
  --query "[?parameters.listOfAllowedLocations].parameters.listOfAllowedLocations.value[]" \
  -o tsv 2>/dev/null | tr -d '\r' | sort -u | tr '\n' ' ' || true)
if [[ -z "${PERMITIDAS// /}" ]]; then
  echo "      Nenhuma politica de regioes encontrada; seguindo com ${LOCATION}."
elif [[ " ${PERMITIDAS} " == *" ${LOCATION} "* && " ${PERMITIDAS} " == *" ${SQL_LOCATION} "* ]]; then
  echo "      OK. Regioes permitidas: ${PERMITIDAS}"
else
  echo
  echo "ERRO: a politica da subscription so permite estas regioes:"
  echo "        ${PERMITIDAS}"
  echo "      Escolha uma delas e rode de novo, por exemplo:"
  echo "        LOCATION=<regiao> bash scripts/01-resource-group.sh"
  echo "      e use o mesmo LOCATION nos scripts seguintes (ou exporte a variavel)."
  exit 1
fi

echo
echo "[3/5] Conferindo o registro dos provedores de recursos..."
# Uma subscription nova pode nao ter o Microsoft.Web ou o Microsoft.Sql
# registrados, e o primeiro "create" falha com MissingSubscriptionRegistration.
for provedor in Microsoft.Web Microsoft.Sql Microsoft.Insights Microsoft.OperationalInsights; do
  ESTADO=$(az provider show --namespace "${provedor}" --query registrationState -o tsv 2>/dev/null || echo "?")
  if [[ "${ESTADO}" == "Registered" ]]; then
    echo "      ${provedor}: registrado"
  else
    echo "      ${provedor}: ${ESTADO} -> registrando (pode levar alguns minutos)..."
    az provider register --namespace "${provedor}" --wait
  fi
done

echo
echo "[4/5] Conferindo se as regioes oferecem todos os servicos usados..."
# A politica pode aceitar uma regiao que ainda nao oferece algum servico
# (regioes novas recebem os servicos aos poucos), e a falha so apareceria
# no meio do provisionamento. Cada provedor informa em quais regioes cada
# tipo de recurso existe, pelo nome de exibicao (ex.: "Chile Central").
nome_de_exibicao() {
  az account list-locations --query "[?name=='$1'].displayName | [0]" -o tsv | tr -d '\r'
}
NOME_LOCATION=$(nome_de_exibicao "${LOCATION}")
NOME_SQL_LOCATION=$(nome_de_exibicao "${SQL_LOCATION}")
if [[ -z "${NOME_LOCATION}" || -z "${NOME_SQL_LOCATION}" ]]; then
  echo "ERRO: regiao desconhecida (${LOCATION} / ${SQL_LOCATION}). Liste as validas com:"
  echo "        az account list-locations --query \"[].name\" -o tsv"
  exit 1
fi
FALTANDO=""
for item in "Microsoft.Web/serverFarms|${NOME_LOCATION}" \
            "Microsoft.Web/sites|${NOME_LOCATION}" \
            "Microsoft.Sql/servers|${NOME_SQL_LOCATION}" \
            "Microsoft.Insights/components|${NOME_LOCATION}" \
            "Microsoft.OperationalInsights/workspaces|${NOME_LOCATION}"; do
  tipo="${item%%|*}"
  regiao="${item#*|}"
  # Uma linha por tipo de recurso: "tipo<TAB>Regiao A|Regiao B|...". A
  # comparacao ignora maiusculas, porque o provedor nao padroniza a caixa.
  REGIOES_DO_TIPO=$(az provider show --namespace "${tipo%%/*}" \
    --query "resourceTypes[?locations].[resourceType, join('|', locations)]" -o tsv | tr -d '\r' \
    | awk -F'\t' -v t="${tipo#*/}" 'tolower($1) == tolower(t) { print $2 }')
  if [[ "|${REGIOES_DO_TIPO,,}|" == *"|${regiao,,}|"* ]]; then
    echo "      ${tipo}: disponivel em ${regiao}"
  else
    echo "      ${tipo}: NAO disponivel em ${regiao}"
    FALTANDO="sim"
  fi
done
if [[ -n "${FALTANDO}" ]]; then
  echo
  echo "ERRO: a regiao nao oferece todos os servicos usados."
  if [[ -n "${PERMITIDAS// /}" ]]; then
    echo "      Regioes permitidas pela subscription: ${PERMITIDAS}"
  fi
  echo "      Escolha outra e rode de novo, por exemplo:"
  echo "        export LOCATION=<regiao>"
  echo "        bash scripts/01-resource-group.sh"
  exit 1
fi

echo
echo "[5/5] Criando o Resource Group ${RESOURCE_GROUP} em ${LOCATION}..."
az group create \
  --name "${RESOURCE_GROUP}" \
  --location "${LOCATION}" \
  --tags "${TAGS[@]}" \
  --query "{Nome:name, Regiao:location, Estado:properties.provisioningState}" \
  --output table

echo
echo "====================================================="
echo " Resource Group pronto: ${RESOURCE_GROUP}"
echo " Proximo passo: bash scripts/02-sql.sh"
echo "====================================================="
