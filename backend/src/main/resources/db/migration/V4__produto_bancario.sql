-- V4 - Produto bancario: chaves PIX, limites diarios, estorno e auditoria.

alter table transacoes drop constraint transacoes_tipo_valido;
alter table transacoes add constraint transacoes_tipo_valido
    check (tipo in ('DEPOSITO', 'SAQUE', 'TRANSFERENCIA', 'PIX', 'ESTORNO'));

-- Senha de movimentacao, separada da senha de acesso. Opcional: enquanto nao
-- for definida, as movimentacoes seguem so com o token. Depois de definida,
-- passa a ser exigida em toda saida de dinheiro.
alter table usuarios add column senha_transacional_hash varchar(255);

create table chaves_pix (
    id        bigserial    primary key,
    conta_id  bigint       not null,
    tipo      varchar(16)  not null,
    valor     varchar(140) not null,
    criado_em timestamptz  not null default now(),

    constraint chaves_pix_conta_fk foreign key (conta_id) references contas (id) on delete cascade,

    -- Uma chave endereca uma unica conta no pais inteiro. Aqui, no Cifra
    -- inteiro -- e a unicidade e do banco, nao de uma checagem em Java.
    constraint chaves_pix_valor_unico unique (valor),
    constraint chaves_pix_tipo_valido check (tipo in ('CPF', 'EMAIL', 'TELEFONE', 'ALEATORIA'))
);

create index chaves_pix_conta_idx on chaves_pix (conta_id);

create table limites (
    conta_id      bigint         primary key,
    limite_diario numeric(18, 2) not null default 5000.00,
    atualizado_em timestamptz    not null default now(),

    constraint limites_conta_fk   foreign key (conta_id) references contas (id) on delete cascade,
    constraint limites_nao_negativo check (limite_diario >= 0)
);

-- Trilha de auditoria. O payload e jsonb para nao precisar de uma migration
-- toda vez que uma acao nova quiser registrar um campo diferente.
create table auditoria (
    id        bigserial   primary key,
    ator_id   bigint,
    acao      varchar(60) not null,
    recurso   varchar(120),
    payload   jsonb,
    ip        varchar(45),
    criado_em timestamptz not null default now()
);

create index auditoria_ator_idx  on auditoria (ator_id, criado_em desc);
create index auditoria_acao_idx  on auditoria (acao, criado_em desc);

-- Toda conta de cliente ja existente ganha seu limite padrao.
insert into limites (conta_id) select id from contas where tipo <> 'LIQUIDACAO';
