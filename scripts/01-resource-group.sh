#!/usr/bin/env bash
# =====================================================================
# ETAPA 1 - Conferencias da subscription + Resource Group
#
# Antes de criar qualquer recurso, confere tres coisas que costumam
# derrubar o provisionamento numa subscription Azure for Students:
#   1) se o az CLI esta autenticado (no Cloud Shell ja esta);
#   2) se a regiao escolhida e permitida pela politica da subscription;
#   3) se os provedores de recursos usados estao registrados.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "[1/4] Conferindo a subscription ativa..."
if ! az account show -o none 2>/dev/null; then
  echo "ERRO: o az CLI nao esta autenticado. Rode 'az login' e repita."
  exit 1
fi
az account show --query "{Subscription:name, Id:id, Usuario:user.name}" -o table

echo
echo "[2/4] Conferindo se a regiao ${LOCATION} e permitida na subscription..."
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
echo "[3/4] Conferindo o registro dos provedores de recursos..."
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
echo "[4/4] Criando o Resource Group ${RESOURCE_GROUP} em ${LOCATION}..."
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
