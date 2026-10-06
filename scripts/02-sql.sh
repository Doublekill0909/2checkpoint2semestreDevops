#!/usr/bin/env bash
# =====================================================================
# ETAPA 2 - Azure SQL Database: servidor, firewall, banco, DDL e usuario
#
#   1) servidor logico Azure SQL (TLS 1.2 minimo)
#   2) regras de firewall: servicos do Azure (o App Service) e o IP de
#      quem esta rodando o script (Cloud Shell ou maquina local)
#   3) banco no tier Basic
#   4) DDL das tabelas (scripts/script_bd.sql), como administrador
#   5) usuario contido da aplicacao, so com leitura e escrita
#      (scripts/script_usuario_app.sql)
#
# Pode ser executado de novo: o que ja existe e reaproveitado, e os dois
# scripts SQL sao idempotentes.
# =====================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
source ./00-vars.sh

if ! command -v sqlcmd >/dev/null 2>&1; then
  echo "ERRO: o sqlcmd nao esta instalado. Rode este script no Azure Cloud Shell"
  echo "      (que ja traz o sqlcmd) ou instale-o: https://aka.ms/sqlcmd"
  exit 1
fi

echo
echo "[1/6] Criando o servidor Azure SQL ${SQL_SERVER} em ${SQL_LOCATION}..."
if az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" -o none 2>/dev/null; then
  echo "      Servidor ja existe; reaproveitando."
else
  # A senha vai como argumento so para o az CLI; o comando nao a imprime.
  az sql server create \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${SQL_SERVER}" \
    --location "${SQL_LOCATION}" \
    --admin-user "${SQL_ADMIN_USER}" \
    --admin-password "${SQL_ADMIN_PASSWORD}" \
    --minimal-tls-version 1.2 \
    --output none
fi
SQL_FQDN=$(az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" --query fullyQualifiedDomainName -o tsv)
az sql server show -g "${RESOURCE_GROUP}" -n "${SQL_SERVER}" \
  --query "{Servidor:name, FQDN:fullyQualifiedDomainName, Regiao:location, TLS:minimalTlsVersion}" -o table

echo
echo "[2/6] Configurando o firewall do servidor..."
# 0.0.0.0 e a regra especial "Permitir servicos e recursos do Azure": e por
# ela que o App Service alcanca o banco. Ela libera qualquer origem dentro
# da Azure, mas a conexao continua exigindo usuario e senha, e o banco so
# aceita TLS 1.2. Em producao, o recomendado seria VNet + Private Endpoint.
az sql server firewall-rule create \
  --resource-group "${RESOURCE_GROUP}" --server "${SQL_SERVER}" \
  --name AllowAzureServices \
  --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0 \
  --output none
echo "      Regra AllowAzureServices (App Service -> Azure SQL) criada."

MEU_IP=$(curl -s --max-time 10 https://api.ipify.org || curl -s --max-time 10 https://ifconfig.me || true)
if [[ "${MEU_IP}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  az sql server firewall-rule create \
    --resource-group "${RESOURCE_GROUP}" --server "${SQL_SERVER}" \
    --name AllowClientIP \
    --start-ip-address "${MEU_IP}" --end-ip-address "${MEU_IP}" \
    --output none
  echo "      Regra AllowClientIP liberando ${MEU_IP} (de onde o sqlcmd vai conectar)."
else
  echo "      Nao consegui descobrir o IP publico desta maquina; seguindo so com a regra da Azure."
fi
az sql server firewall-rule list -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" \
  --query "[].{Regra:name, Inicio:startIpAddress, Fim:endIpAddress}" -o table

echo
echo "[3/6] Criando o banco ${SQL_DB} (${SQL_SERVICE_OBJECTIVE})..."
if az sql db show -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" -n "${SQL_DB}" -o none 2>/dev/null; then
  echo "      Banco ja existe; reaproveitando."
else
  # Backup local (LRS): mais barato e disponivel em qualquer regiao.
  az sql db create \
    --resource-group "${RESOURCE_GROUP}" \
    --server "${SQL_SERVER}" \
    --name "${SQL_DB}" \
    --service-objective "${SQL_SERVICE_OBJECTIVE}" \
    --backup-storage-redundancy Local \
    --tags "${TAGS[@]}" \
    --output none
fi
az sql db show -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" -n "${SQL_DB}" \
  --query "{Banco:name, Tier:currentServiceObjectiveName, Status:status, Collation:collation}" -o table

# A senha do administrador chega ao sqlcmd pela variavel SQLCMDPASSWORD,
# e nao pela linha de comando (-P), que ficaria visivel na lista de processos.
executar_sql() {
  local arquivo="$1"
  SQLCMDPASSWORD="${SQL_ADMIN_PASSWORD}" sqlcmd \
    -S "tcp:${SQL_FQDN},1433" -d "${SQL_DB}" -U "${SQL_ADMIN_USER}" \
    -b -W -s "|" -i "${arquivo}"
}

echo
echo "[4/6] Aguardando o banco aceitar conexoes..."
# Logo depois da criacao, o gateway pode recusar as primeiras conexoes, e
# uma regra de firewall nova leva alguns segundos para valer.
for tentativa in $(seq 1 12); do
  if SQLCMDPASSWORD="${SQL_ADMIN_PASSWORD}" sqlcmd -S "tcp:${SQL_FQDN},1433" -d "${SQL_DB}" \
       -U "${SQL_ADMIN_USER}" -b -Q "SELECT 1" >/dev/null 2>&1; then
    echo "      Conexao OK."
    break
  fi
  if (( tentativa == 12 )); then
    echo "ERRO: o banco nao aceitou conexoes. Confira o firewall e as credenciais do .env."
    exit 1
  fi
  echo "      Ainda nao conectou, nova tentativa em 10s (${tentativa}/12)..."
  sleep 10
done

echo
echo "[5/6] Executando o DDL (scripts/script_bd.sql) como administrador..."
executar_sql ./script_bd.sql

echo
echo "[6/6] Criando o usuario da aplicacao ${APP_DB_USER} (so leitura e escrita)..."
# O template e preenchido em memoria e gravado em .generated/ com
# permissao 600, so pelo tempo da execucao. Aspas simples da senha sao
# dobradas para nao quebrar o literal N'...' do T-SQL.
RENDERIZADO="${GENERATED_DIR}/usuario_app.sql"
trap 'rm -f "${RENDERIZADO}"' EXIT
SENHA_SQL="${APP_DB_PASSWORD//\'/\'\'}"
CONTEUDO="$(<./script_usuario_app.sql)"
CONTEUDO="${CONTEUDO//__APP_DB_USER__/"${APP_DB_USER}"}"
CONTEUDO="${CONTEUDO//__APP_DB_PASSWORD__/"${SENHA_SQL}"}"
( umask 077 && printf '%s\n' "${CONTEUDO}" > "${RENDERIZADO}" )
executar_sql "${RENDERIZADO}"
rm -f "${RENDERIZADO}"

echo
echo "====================================================="
echo " Servidor ..: ${SQL_FQDN}"
echo " Banco .....: ${SQL_DB} (tabelas tb_cliente e tb_conta)"
echo " Usuario app: ${APP_DB_USER} (db_datareader + db_datawriter)"
echo " Proximo passo: bash scripts/03-monitoramento.sh"
echo "====================================================="
