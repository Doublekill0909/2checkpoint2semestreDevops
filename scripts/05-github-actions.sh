#!/usr/bin/env bash
# =====================================================================
# ETAPA 5 - Deploy automatizado com GitHub Actions
#
#   1) confere o GitHub CLI (gh) autenticado
#   2) grava no repositorio a credencial de publicacao da Web App (secret
#      AZURE_WEBAPP_PUBLISH_PROFILE) e o nome dela (variable AZURE_WEBAPP_NAME)
#   3) dispara o workflow .github/workflows/deploy.yml
#   4) acompanha build, testes e deploy ate o fim
#   5) confere /actuator/health da aplicacao ja na nuvem
#
# Depois desta etapa, todo push na main repete build, testes e deploy
# sozinho, sem nenhum comando manual.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

echo
echo "[1/5] Conferindo o GitHub CLI..."
if ! command -v gh >/dev/null 2>&1; then
  echo "ERRO: o GitHub CLI (gh) nao esta instalado. O Azure Cloud Shell ja traz o gh."
  exit 1
fi
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "      O gh ainda nao esta autenticado. Copie o codigo exibido e abra"
  echo "      https://github.com/login/device em qualquer navegador."
  gh auth login --hostname github.com --git-protocol https --web
fi
gh repo view "${GITHUB_REPO}" --json nameWithOwner,url \
  --jq '"      Repositorio: \(.nameWithOwner) (\(.url))"'

echo
echo "[2/5] Gravando a credencial de publicacao e o nome da Web App no repositorio..."
# O publish profile vai do az CLI direto para o gh por pipe: nao passa por
# arquivo, nao aparece no terminal e fica guardado criptografado como secret.
az webapp deployment list-publishing-profiles \
  --resource-group "${RESOURCE_GROUP}" --name "${WEBAPP_NAME}" --xml \
  | gh secret set AZURE_WEBAPP_PUBLISH_PROFILE --repo "${GITHUB_REPO}"
gh variable set AZURE_WEBAPP_NAME --repo "${GITHUB_REPO}" --body "${WEBAPP_NAME}"
echo "      Secrets do repositorio:"
gh secret list --repo "${GITHUB_REPO}" | sed 's/^/        /'
echo "      Variables do repositorio:"
gh variable list --repo "${GITHUB_REPO}" | sed 's/^/        /'

echo
echo "[3/5] Disparando o workflow ${GITHUB_WORKFLOW} na branch main..."
# Um minuto de folga no horario de referencia cobre diferenca de relogio
# entre esta maquina e o GitHub.
REFERENCIA=$(date -u -d '-1 minute' +%Y-%m-%dT%H:%M:%SZ)
gh workflow run "${GITHUB_WORKFLOW}" --repo "${GITHUB_REPO}" --ref main

RUN_ID=""
for _ in $(seq 1 30); do
  RUN_ID=$(gh run list --repo "${GITHUB_REPO}" --workflow "${GITHUB_WORKFLOW}" \
    --event workflow_dispatch --limit 5 --json databaseId,createdAt \
    --jq "[.[] | select(.createdAt >= \"${REFERENCIA}\")] | .[0].databaseId // empty")
  [[ -n "${RUN_ID}" ]] && break
  sleep 3
done
if [[ -z "${RUN_ID}" ]]; then
  echo "ERRO: a execucao disparada nao apareceu. Confira a aba Actions do repositorio."
  exit 1
fi
echo "      Execucao: https://github.com/${GITHUB_REPO}/actions/runs/${RUN_ID}"

echo
echo "[4/5] Acompanhando build, testes e deploy..."
if ! gh run watch "${RUN_ID}" --repo "${GITHUB_REPO}" --interval 10 --exit-status; then
  echo
  echo "ERRO: o workflow falhou. Para ver o trecho do log com o erro:"
  echo "  gh run view ${RUN_ID} --repo ${GITHUB_REPO} --log-failed"
  exit 1
fi

echo
echo "[5/5] Conferindo a aplicacao na nuvem..."
WEBAPP_HOST=$(az webapp show -g "${RESOURCE_GROUP}" -n "${WEBAPP_NAME}" --query defaultHostName -o tsv)
for tentativa in $(seq 1 30); do
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "https://${WEBAPP_HOST}/actuator/health" || true)
  if [[ "${STATUS}" == "200" ]]; then
    echo "      /actuator/health: $(curl -s --max-time 20 "https://${WEBAPP_HOST}/actuator/health")"
    break
  fi
  if (( tentativa == 30 )); then
    echo "AVISO: a aplicacao ainda nao respondeu 200. Veja a subida da JVM com:"
    echo "  az webapp log tail -g ${RESOURCE_GROUP} -n ${WEBAPP_NAME}"
    exit 1
  fi
  echo "      HTTP ${STATUS:-sem resposta}; a JVM ainda esta subindo, nova tentativa em 10s (${tentativa}/30)..."
  sleep 10
done

echo
echo "====================================================="
echo " Aplicacao no ar: https://${WEBAPP_HOST}"
echo " API REST ......: https://${WEBAPP_HOST}/api/clientes"
echo " Health ........: https://${WEBAPP_HOST}/actuator/health"
echo " Proximo passo: bash scripts/06-status.sh"
echo "====================================================="
