#!/usr/bin/env bash
# =====================================================================
# Evidencia do CRUD direto no Azure SQL Database.
#
# Roda as consultas de scripts/consultas_crud.sql (SELECT em tb_cliente,
# tb_conta com JOIN pelo titular e totais). Use depois de cada operacao
# feita na aplicacao -- cadastrar, editar, excluir -- para mostrar no
# video que o dado chegou ao banco na nuvem.
#
# Conecta com o usuario da aplicacao (APP_DB_USER), o mesmo que o App
# Service usa, e nao com o administrador.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh >/dev/null

if ! command -v sqlcmd >/dev/null 2>&1; then
  echo "ERRO: o sqlcmd nao esta instalado. Rode no Azure Cloud Shell ou use o"
  echo "      Query editor do banco no portal com scripts/consultas_crud.sql."
  exit 1
fi

SQL_FQDN=$(az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" --query fullyQualifiedDomainName -o tsv)

echo "Consultando ${SQL_FQDN} / ${SQL_DB} em $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S') (Brasilia)"
SQLCMDPASSWORD="${APP_DB_PASSWORD}" sqlcmd \
  -S "tcp:${SQL_FQDN},1433" -d "${SQL_DB}" -U "${APP_DB_USER}" \
  -b -W -s "|" -i ./consultas_crud.sql
