-- =====================================================================
-- DimDim - consultas de evidencia do CRUD no Azure SQL Database
--
-- Rode depois de cada operacao feita na aplicacao para mostrar o dado
-- persistido na nuvem:
--     bash scripts/07-consultar-banco.sh
-- ou cole este arquivo no Query editor do banco, no portal da Azure.
-- =====================================================================
SET NOCOUNT ON;

PRINT '=== tb_cliente ===';
SELECT id,
       nome,
       cpf,
       email,
       telefone,
       data_nascimento,
       data_cadastro
FROM dbo.tb_cliente
ORDER BY id;

PRINT '=== tb_conta, com o titular obtido pela chave estrangeira cliente_id ===';
SELECT c.id,
       c.agencia,
       c.numero,
       c.tipo,
       c.saldo,
       c.ativa,
       c.data_abertura,
       c.cliente_id,
       cl.nome AS titular
FROM dbo.tb_conta AS c
JOIN dbo.tb_cliente AS cl ON cl.id = c.cliente_id
ORDER BY c.id;

PRINT '=== totais ===';
SELECT (SELECT COUNT(*) FROM dbo.tb_cliente) AS clientes,
       (SELECT COUNT(*) FROM dbo.tb_conta) AS contas,
       (SELECT COUNT(*) FROM dbo.tb_conta WHERE ativa = 1) AS contas_ativas,
       (SELECT COALESCE(SUM(saldo), 0) FROM dbo.tb_conta WHERE ativa = 1) AS saldo_contas_ativas;
