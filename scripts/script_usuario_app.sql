-- =====================================================================
-- DimDim - usuario da aplicacao no Azure SQL Database
--
-- Cria o usuario CONTIDO (contained database user) com que o App Service
-- se conecta ao banco. Ele existe somente dentro deste banco, nao tem
-- login no servidor nem acesso ao master, e so le e grava dados
-- (db_datareader + db_datawriter): nao consegue criar, alterar ou apagar
-- tabelas. O administrador do servidor fica restrito aos scripts.
--
-- TEMPLATE: os marcadores __APP_DB_USER__ e __APP_DB_PASSWORD__ sao
-- preenchidos pelo scripts/02-sql.sh com os valores do .env, em um arquivo
-- temporario dentro de scripts/.generated/ (ignorado pelo Git) que e
-- apagado logo apos a execucao. Nenhuma credencial fica neste arquivo.
--
-- Idempotente: se o usuario ja existe, apenas redefine a senha.
-- =====================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DATABASE_PRINCIPAL_ID(N'__APP_DB_USER__') IS NULL
    CREATE USER [__APP_DB_USER__] WITH PASSWORD = N'__APP_DB_PASSWORD__';
ELSE
    ALTER USER [__APP_DB_USER__] WITH PASSWORD = N'__APP_DB_PASSWORD__';

ALTER ROLE db_datareader ADD MEMBER [__APP_DB_USER__];
ALTER ROLE db_datawriter ADD MEMBER [__APP_DB_USER__];

-- Conferencia: o usuario e os papeis que ele recebeu.
SELECT u.name AS usuario,
       u.type_desc AS tipo,
       u.authentication_type_desc AS autenticacao,
       r.name AS papel
FROM sys.database_principals AS u
JOIN sys.database_role_members AS m ON m.member_principal_id = u.principal_id
JOIN sys.database_principals AS r ON r.principal_id = m.role_principal_id
WHERE u.name = N'__APP_DB_USER__'
ORDER BY r.name;
