-- =====================================================================
-- DimDim - DDL das tabelas (Azure SQL Database)
-- Checkpoint FIAP - DevOps Tools & Cloud Computing
-- 2o Checkpoint do 2o semestre - Aplicacoes e Banco em Nuvem
-- Grupo Vitalis - representante RM566234
--
-- Executado pelo scripts/02-sql.sh com o sqlcmd, conectado como o
-- administrador do servidor dentro do banco da aplicacao.
--
-- Este script e a FONTE DA VERDADE do schema: a aplicacao nao cria
-- tabelas. Ela sobe com spring.jpa.hibernate.ddl-auto=validate, ou seja,
-- o Hibernate confere tabelas, colunas e tipos e recusa iniciar se o
-- banco divergir das entidades Cliente e Conta.
--
-- Idempotente: cada tabela so e criada se ainda nao existir, entao rodar
-- o script de novo nao apaga dados. Tudo roda em uma unica transacao com
-- XACT_ABORT ON, de modo que uma falha no meio desfaz o script inteiro.
-- Para recriar do zero (APAGA OS DADOS), rode antes:
--     DROP TABLE IF EXISTS dbo.tb_conta;
--     DROP TABLE IF EXISTS dbo.tb_cliente;
--
-- 2 tabelas | 1 chave estrangeira (cliente 1:N conta) | comentarios
-- (MS_Description) em todas as tabelas e colunas
-- =====================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

-- =====================================================================
-- 1) TB_CLIENTE - cliente do banco, titular das contas
-- =====================================================================
IF OBJECT_ID(N'dbo.tb_cliente', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_cliente (
        id               BIGINT         IDENTITY(1,1) NOT NULL,
        nome             NVARCHAR(120)  NOT NULL,
        cpf              NVARCHAR(11)   NOT NULL,
        email            NVARCHAR(120)  NOT NULL,
        telefone         NVARCHAR(20)   NULL,
        data_nascimento  DATE           NOT NULL,
        data_cadastro    DATETIME2(0)   NOT NULL,
        CONSTRAINT pk_cliente        PRIMARY KEY (id),
        CONSTRAINT uk_cliente_cpf    UNIQUE (cpf),
        CONSTRAINT uk_cliente_email  UNIQUE (email),
        CONSTRAINT ck_cliente_cpf    CHECK (LEN(cpf) = 11 AND cpf NOT LIKE N'%[^0-9]%')
    );

    -- O Azure SQL roda em UTC; o valor padrao usa o horario de Brasilia,
    -- o mesmo fuso com que a aplicacao grava (classe fiap.dimdim.config.Datas).
    ALTER TABLE dbo.tb_cliente ADD CONSTRAINT df_cliente_data_cadastro
        DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time' AS DATETIME2(0)))
        FOR data_cadastro;

    EXEC sys.sp_addextendedproperty N'MS_Description', N'Cliente do banco DimDim, titular de uma ou mais contas.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Identificador do cliente, gerado pelo banco (IDENTITY).',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'id';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Nome completo do cliente.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'nome';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'CPF com 11 digitos, sem pontuacao. Unico no banco.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'cpf';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'E-mail em minusculas. Unico no banco.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'email';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Telefone de contato (opcional).',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'telefone';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Data de nascimento do cliente.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'data_nascimento';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Data e hora do cadastro, no horario de Brasilia.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_cliente', N'COLUMN', N'data_cadastro';
END;

-- =====================================================================
-- 2) TB_CONTA - conta bancaria (N contas para 1 cliente)
-- =====================================================================
IF OBJECT_ID(N'dbo.tb_conta', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_conta (
        id               BIGINT         IDENTITY(1,1) NOT NULL,
        cliente_id       BIGINT         NOT NULL,
        agencia          NVARCHAR(4)    NOT NULL,
        numero           NVARCHAR(7)    NOT NULL,
        tipo             NVARCHAR(20)   NOT NULL,
        saldo            DECIMAL(15,2)  NOT NULL,
        ativa            BIT            NOT NULL,
        data_abertura    DATE           NOT NULL,
        CONSTRAINT pk_conta                 PRIMARY KEY (id),
        CONSTRAINT fk_conta_cliente         FOREIGN KEY (cliente_id) REFERENCES dbo.tb_cliente (id)
                                            ON DELETE NO ACTION,
        CONSTRAINT uk_conta_agencia_numero  UNIQUE (agencia, numero),
        CONSTRAINT ck_conta_tipo            CHECK (tipo IN (N'CORRENTE', N'POUPANCA', N'SALARIO')),
        CONSTRAINT ck_conta_saldo           CHECK (saldo >= 0),
        CONSTRAINT ck_conta_numero          CHECK (numero LIKE N'[0-9][0-9][0-9][0-9][0-9]-[0-9]')
    );

    ALTER TABLE dbo.tb_conta ADD CONSTRAINT df_conta_agencia DEFAULT (N'0001') FOR agencia;
    ALTER TABLE dbo.tb_conta ADD CONSTRAINT df_conta_saldo DEFAULT (0) FOR saldo;
    ALTER TABLE dbo.tb_conta ADD CONSTRAINT df_conta_ativa DEFAULT (1) FOR ativa;
    ALTER TABLE dbo.tb_conta ADD CONSTRAINT df_conta_data_abertura
        DEFAULT (CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'E. South America Standard Time' AS DATE))
        FOR data_abertura;

    -- Indice da chave estrangeira: acelera "contas do cliente X" e a
    -- verificacao da FK quando um cliente e excluido.
    CREATE INDEX ix_conta_cliente_id ON dbo.tb_conta (cliente_id);

    EXEC sys.sp_addextendedproperty N'MS_Description', N'Conta bancaria de um cliente do DimDim.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Identificador da conta, gerado pelo banco (IDENTITY).',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'id';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Cliente titular da conta (FK para tb_cliente.id). Nao muda depois da abertura.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'cliente_id';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Agencia da conta. O DimDim e um banco digital de agencia unica (0001).',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'agencia';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Numero da conta no formato 00000-0, com digito verificador modulo 11.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'numero';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Modalidade da conta: CORRENTE, POUPANCA ou SALARIO.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'tipo';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Saldo em reais. Nunca negativo.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'saldo';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'1 = conta ativa, 0 = conta inativa (fora do saldo total do painel).',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'ativa';
    EXEC sys.sp_addextendedproperty N'MS_Description', N'Data de abertura da conta, no horario de Brasilia.',
        N'SCHEMA', N'dbo', N'TABLE', N'tb_conta', N'COLUMN', N'data_abertura';
END;

COMMIT TRANSACTION;

-- Conferencia: tabelas e colunas com os respectivos comentarios.
SELECT t.name AS tabela,
       COALESCE(c.name, N'(tabela)') AS coluna,
       CAST(ep.value AS NVARCHAR(200)) AS comentario
FROM sys.extended_properties AS ep
JOIN sys.tables AS t ON t.object_id = ep.major_id
LEFT JOIN sys.columns AS c ON c.object_id = ep.major_id AND c.column_id = ep.minor_id
WHERE ep.name = N'MS_Description'
  AND t.name IN (N'tb_cliente', N'tb_conta')
ORDER BY t.name, ep.minor_id;
