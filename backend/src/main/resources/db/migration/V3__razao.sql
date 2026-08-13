-- V3 - O razao: transacoes, lancamentos e a projecao de saldo.
--
-- Regra central do sistema: dinheiro nao aparece nem some. Toda transacao
-- gera lancamentos assinados que somam exatamente zero.
--
-- Isso responde uma pergunta que costuma passar batido: de onde vem o dinheiro
-- de um deposito? De algum lugar ele tem que sair. A resposta e a conta de
-- liquidacao -- a fronteira contabil entre o Cifra e o mundo. Depositar credita
-- o cliente e debita a liquidacao; sacar faz o contrario. O sistema como um
-- todo continua somando zero, e o saldo da liquidacao e exatamente quanto o
-- Cifra deve ao mundo externo.

-- A conta de liquidacao nao tem titular e pode ficar negativa.
alter table contas alter column usuario_id drop not null;
alter table contas drop constraint contas_tipo_valido;
alter table contas add constraint contas_tipo_valido
    check (tipo in ('CORRENTE', 'POUPANCA', 'LIQUIDACAO'));
alter table contas add constraint contas_titular_obrigatorio
    check (tipo = 'LIQUIDACAO' or usuario_id is not null);

create table transacoes (
    id              bigserial     primary key,
    tipo            varchar(16)   not null,
    status          varchar(16)   not null,
    valor_total     numeric(18, 2) not null,
    descricao       varchar(180),
    idempotency_key varchar(120),
    estorno_de_id   bigint,
    criado_em       timestamptz   not null default now(),
    liquidado_em    timestamptz,

    -- A garantia de idempotencia e esta linha, nao um "if" na aplicacao.
    -- Duas requisicoes simultaneas com a mesma chave: uma grava, a outra
    -- leva violacao de unicidade e devolve o resultado da primeira.
    constraint transacoes_idempotencia unique (idempotency_key),

    constraint transacoes_estorno_fk foreign key (estorno_de_id) references transacoes (id),
    constraint transacoes_valor_positivo check (valor_total > 0),
    constraint transacoes_tipo_valido   check (tipo   in ('DEPOSITO', 'SAQUE', 'TRANSFERENCIA', 'ESTORNO')),
    constraint transacoes_status_valido check (status in ('PENDENTE', 'LIQUIDADA', 'REJEITADA', 'ESTORNADA'))
);

create index transacoes_criado_em_idx on transacoes (criado_em desc);

-- Append-only. Nao existe update nem delete aqui: corrigir um lancamento e
-- escrever outro em sentido contrario, do jeito que se faz num livro contabil.
create table lancamentos (
    id           bigserial      primary key,
    transacao_id bigint         not null,
    conta_id     bigint         not null,
    valor        numeric(18, 2) not null,
    criado_em    timestamptz    not null default now(),

    constraint lancamentos_transacao_fk foreign key (transacao_id) references transacoes (id),
    constraint lancamentos_conta_fk     foreign key (conta_id)     references contas (id),
    constraint lancamentos_valor_nao_zero check (valor <> 0)
);

create index lancamentos_extrato_idx   on lancamentos (conta_id, criado_em desc);
create index lancamentos_transacao_idx on lancamentos (transacao_id);

-- Projecao do razao, nao a fonte da verdade. Existe porque somar o razao
-- inteiro a cada consulta nao escala; um job de reconciliacao confere se esta
-- coluna continua batendo com sum(lancamentos.valor).
create table saldos (
    conta_id         bigint         primary key,
    saldo            numeric(18, 2) not null default 0,
    permite_negativo boolean        not null default false,
    versao           bigint         not null default 0,
    atualizado_em    timestamptz    not null default now(),

    constraint saldos_conta_fk foreign key (conta_id) references contas (id),

    -- Ultima linha de defesa contra saldo furado. Se um caminho de codigo
    -- escapar da validacao, o banco recusa.
    constraint saldos_nao_negativo check (permite_negativo or saldo >= 0)
);

-- Toda conta ja existente ganha sua linha de saldo.
insert into saldos (conta_id, saldo) select id, 0 from contas;

-- A fronteira com o mundo externo.
insert into contas (usuario_id, agencia, numero, tipo, status)
values (null, '0000', '000000001', 'LIQUIDACAO', 'ATIVA');

insert into saldos (conta_id, saldo, permite_negativo)
select id, 0, true from contas where tipo = 'LIQUIDACAO';
