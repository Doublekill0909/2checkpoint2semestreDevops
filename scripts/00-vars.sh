#!/usr/bin/env bash
# =====================================================================
# Variaveis compartilhadas por todos os scripts do provisionamento.
# NAO executa nada sozinho -- e carregado com "source" pelos demais.
#
#   RM do representante do grupo: 566234 (Joao Guilherme Carvalho Novaes)
#   Prefixo dos recursos na Azure: rm566234
#
# Qualquer valor marcado com ${VAR:-padrao} pode ser trocado na hora da
# execucao, sem editar este arquivo. Exemplo, para outra regiao:
#     LOCATION=eastus2 bash scripts/01-resource-group.sh
# =====================================================================

# "return 1 2>/dev/null || exit 1": o return encerra quando o arquivo e
# carregado com source; o exit so roda se alguem executar o arquivo direto.
# shellcheck disable=SC2317

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPTS_DIR}/.." && pwd)"
export SCRIPTS_DIR PROJECT_ROOT

# ---------------------------------------------------------------------
# Identificacao do grupo
# ---------------------------------------------------------------------
export RM="rm566234"
export GRUPO="vitalis"
export PROJETO="dimdim"

# ---------------------------------------------------------------------
# Localizacao e Resource Group
#
# chilecentral e uma das regioes aceitas pela subscription Azure for
# Students do grupo. Essas subscriptions so permitem algumas regioes
# (politica "Allowed resource deployment regions"); o 01-resource-group.sh
# confere a politica e se a regiao oferece todos os servicos usados antes
# de criar qualquer coisa.
# ---------------------------------------------------------------------
export LOCATION="${LOCATION:-chilecentral}"
export RESOURCE_GROUP="${RESOURCE_GROUP:-rg-${RM}-${PROJETO}}"

# ---------------------------------------------------------------------
# Azure SQL Database (banco PaaS, nada containerizado)
#
# O nome do servidor vira <nome>.database.windows.net e precisa ser unico
# em toda a Azure. SQL_LOCATION existe para o caso raro de a regiao do App
# Service nao aceitar novos servidores SQL na subscription de estudante.
# ---------------------------------------------------------------------
export SQL_LOCATION="${SQL_LOCATION:-${LOCATION}}"
export SQL_SERVER="${SQL_SERVER:-sql-${RM}-${PROJETO}}"
export SQL_DB="${SQL_DB:-db-${PROJETO}}"
# Basic: 5 DTUs e 2 GB, o menor tier do modelo DTU (poucos dolares por mes,
# cobrados por hora). Sem pausa automatica, o banco responde na hora no video.
export SQL_SERVICE_OBJECTIVE="${SQL_SERVICE_OBJECTIVE:-Basic}"

# ---------------------------------------------------------------------
# App Service (PaaS, Linux, Java SE 21)
#
# O nome da Web App vira https://<nome>.azurewebsites.net e tambem precisa
# ser unico em toda a Azure. B1 e o menor plano com Always On, que mantem
# a JVM carregada e evita a primeira requisicao lenta durante o video.
# ---------------------------------------------------------------------
export APP_SERVICE_PLAN="${APP_SERVICE_PLAN:-plan-${RM}-${PROJETO}}"
export APP_SERVICE_SKU="${APP_SERVICE_SKU:-B1}"
export WEBAPP_NAME="${WEBAPP_NAME:-${RM}-${PROJETO}}"
export WEBAPP_RUNTIME="JAVA:21-java21"

# ---------------------------------------------------------------------
# Monitoramento: Application Insights baseado em workspace. App e banco
# mandam telemetria e logs para o mesmo Log Analytics workspace.
# ---------------------------------------------------------------------
export LOG_ANALYTICS="${LOG_ANALYTICS:-log-${RM}-${PROJETO}}"
export APP_INSIGHTS="${APP_INSIGHTS:-appi-${RM}-${PROJETO}}"

# ---------------------------------------------------------------------
# GitHub Actions: o repositorio vem do "origin" do clone, de modo que o
# deploy funciona tambem a partir de um fork.
# ---------------------------------------------------------------------
repositorio_do_origin() {
  git -C "${PROJECT_ROOT}" remote get-url origin 2>/dev/null \
    | sed -E 's#^(git@github\.com:|https://github\.com/)##; s#\.git$##'
}
GITHUB_REPO="${GITHUB_REPO:-$(repositorio_do_origin)}"
export GITHUB_REPO="${GITHUB_REPO:-Doublekill0909/2checkpoint2semestreDevops}"
export GITHUB_WORKFLOW="deploy.yml"

# Tags aplicadas em todos os recursos, para identificar o checkpoint no portal.
# shellcheck disable=SC2034
TAGS=(projeto="${PROJETO}" grupo="${GRUPO}" rm="${RM}" disciplina=devops-cloud)

# ---------------------------------------------------------------------
# Credenciais -- lidas do arquivo .env da raiz do projeto.
# NUNCA sao escritas neste arquivo nem commitadas no Git.
# ---------------------------------------------------------------------
ENV_FILE="${PROJECT_ROOT}/.env"
if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # O tr descarta o CR de um .env salvo no Windows (CRLF); sem isso, cada
  # valor terminaria com um "\r" invisivel e a validacao abaixo o recusaria.
  # shellcheck disable=SC1090
  source <(tr -d '\r' < "${ENV_FILE}")
  set +a
else
  echo "ERRO: arquivo .env nao encontrado em ${ENV_FILE}"
  echo "      Rode:  cp .env.example .env   e preencha usuarios e senhas."
  return 1 2>/dev/null || exit 1
fi

: "${SQL_ADMIN_USER:?SQL_ADMIN_USER nao definido no .env}"
: "${SQL_ADMIN_PASSWORD:?SQL_ADMIN_PASSWORD nao definido no .env}"
: "${APP_DB_USER:?APP_DB_USER nao definido no .env}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD nao definido no .env}"

# O Azure SQL recusa senhas fracas, mas so depois de alguns minutos de
# provisionamento. Conferir aqui economiza uma tentativa perdida: 8 a 128
# caracteres, pelo menos 3 das 4 categorias e sem conter o nome do usuario.
senha_valida() {
  local senha="$1" usuario="$2" categorias=0
  [[ ${#senha} -ge 8 && ${#senha} -le 128 ]] || return 1
  [[ "${senha}" =~ [A-Z] ]] && categorias=$((categorias + 1))
  [[ "${senha}" =~ [a-z] ]] && categorias=$((categorias + 1))
  [[ "${senha}" =~ [0-9] ]] && categorias=$((categorias + 1))
  [[ "${senha}" =~ [^A-Za-z0-9] ]] && categorias=$((categorias + 1))
  [[ ${categorias} -ge 3 ]] || return 1
  [[ "${senha,,}" != *"${usuario,,}"* ]]
}

for par in "SQL_ADMIN_USER:SQL_ADMIN_PASSWORD" "APP_DB_USER:APP_DB_PASSWORD"; do
  var_usuario="${par%%:*}"
  var_senha="${par##*:}"
  if [[ ! "${!var_usuario}" =~ ^[A-Za-z][A-Za-z0-9_]{2,63}$ ]]; then
    echo "ERRO: ${var_usuario} deve comecar com letra e ter so letras, numeros ou _ (3 a 64)."
    return 1 2>/dev/null || exit 1
  fi
  if ! senha_valida "${!var_senha}" "${!var_usuario}"; then
    echo "ERRO: ${var_senha} nao atende a politica de senha do Azure SQL:"
    echo "      8 a 128 caracteres, com pelo menos 3 destes 4 grupos -- maiusculas,"
    echo "      minusculas, numeros e simbolos -- e sem conter o nome do usuario."
    return 1 2>/dev/null || exit 1
  fi
done
unset par var_usuario var_senha

# A aplicacao conecta com um usuario proprio, de minimo privilegio. Com o
# mesmo nome do administrador, o usuario contido criado pelo 02-sql.sh
# passaria a responder pelo login do administrador dentro do banco, e o
# administrador deixaria de conseguir conectar nele.
if [[ "${APP_DB_USER,,}" == "${SQL_ADMIN_USER,,}" ]]; then
  echo "ERRO: APP_DB_USER e SQL_ADMIN_USER precisam ser usuarios diferentes no .env."
  return 1 2>/dev/null || exit 1
fi

case "${SQL_ADMIN_USER,,}" in
  admin|administrator|sa|root|dbmanager|loginmanager|guest|public|dbo)
    echo "ERRO: '${SQL_ADMIN_USER}' e um nome reservado pelo Azure SQL. Escolha outro no .env."
    return 1 2>/dev/null || exit 1 ;;
esac

# ---------------------------------------------------------------------
# Acesso ao Azure SQL a partir de onde os scripts rodam (Cloud Shell).
# Usado pelo 02-sql.sh e pelo 07-consultar-banco.sh.
# ---------------------------------------------------------------------

# Libera o IP publico desta maquina na regra de firewall AllowClientIP. O
# Cloud Shell ganha um IP novo a cada sessao, entao a regra criada pelo
# 02-sql.sh deixa de valer quando a sessao expira; por isso ela e conferida
# de novo antes de cada consulta.
liberar_ip_no_firewall() {
  local ip anterior
  ip=$(curl -s --max-time 10 https://api.ipify.org || curl -s --max-time 10 https://ifconfig.me || true)
  if [[ ! "${ip}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "      Nao consegui descobrir o IP publico desta maquina; a regra AllowClientIP nao foi atualizada."
    return 0
  fi
  anterior=$(az sql server firewall-rule show -g "${RESOURCE_GROUP}" -s "${SQL_SERVER}" \
    -n AllowClientIP --query startIpAddress -o tsv 2>/dev/null || true)
  if [[ "${anterior}" == "${ip}" ]]; then
    echo "      Regra AllowClientIP ja libera ${ip}."
    return 0
  fi
  az sql server firewall-rule create \
    --resource-group "${RESOURCE_GROUP}" --server "${SQL_SERVER}" \
    --name AllowClientIP \
    --start-ip-address "${ip}" --end-ip-address "${ip}" \
    --output none
  echo "      Regra AllowClientIP liberando ${ip} (de onde o sqlcmd vai conectar)."
}

# Espera o banco aceitar uma conexao do usuario informado. Logo depois da
# criacao do banco, o gateway pode recusar as primeiras conexoes, e uma
# regra de firewall nova leva alguns segundos para valer. A senha chega ao
# sqlcmd pela variavel SQLCMDPASSWORD, e nao pela linha de comando (-P),
# que ficaria visivel na lista de processos.
aguardar_banco() {
  local fqdn="$1" usuario="$2" senha="$3" tentativa
  for tentativa in $(seq 1 12); do
    if SQLCMDPASSWORD="${senha}" sqlcmd -S "tcp:${fqdn},1433" -d "${SQL_DB}" \
         -U "${usuario}" -b -Q "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    if (( tentativa == 12 )); then
      echo "ERRO: o banco nao aceitou conexoes de ${usuario}. Confira o firewall e as credenciais do .env."
      return 1
    fi
    echo "      Ainda nao conectou, nova tentativa em 10s (${tentativa}/12)..."
    sleep 10
  done
}

# Pasta para artefatos gerados em tempo de execucao (SQL com senha).
# Esta no .gitignore -- nada daqui vai para o repositorio.
export GENERATED_DIR="${SCRIPTS_DIR}/.generated"
mkdir -p "${GENERATED_DIR}"
chmod 700 "${GENERATED_DIR}"

echo "--------------------------------------------------------"
echo " Grupo .............: ${GRUPO} (${RM})"
echo " Resource Group ....: ${RESOURCE_GROUP}"
echo " Regiao ............: ${LOCATION}"
echo " Azure SQL .........: ${SQL_SERVER} / ${SQL_DB} (${SQL_SERVICE_OBJECTIVE}, ${SQL_LOCATION})"
echo " App Service .......: ${WEBAPP_NAME} (${WEBAPP_RUNTIME}, plano ${APP_SERVICE_SKU})"
echo " Monitoramento .....: ${APP_INSIGHTS} + ${LOG_ANALYTICS}"
echo " Repositorio .......: ${GITHUB_REPO}"
echo "--------------------------------------------------------"
