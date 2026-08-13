-- V1 - Identidade: usuarios e contas.
--
-- Todo o schema do Cifra nasce em migration. O Hibernate roda com
-- ddl-auto=validate e nunca cria nem altera nada por conta propria.

create table usuarios (
    id            bigserial    primary key,
    nome          varchar(120) not null,
    -- varchar, nunca char: o bpchar do Postgres preenche com espacos ate o tamanho
    -- fixo e o validador de esquema do Hibernate espera varchar para String.
    cpf           varchar(11)  not null,
    email         varchar(180) not null,
    senha_hash    varchar(255) not null,
    status        varchar(24)  not null default 'PENDENTE_VERIFICACAO',
    criado_em     timestamptz  not null default now(),
    atualizado_em timestamptz  not null default now(),

    constraint usuarios_cpf_unico     unique (cpf),
    constraint usuarios_email_unico    unique (email),
    constraint usuarios_cpf_numerico   check (cpf ~ '^[0-9]{11}$'),
    constraint usuarios_status_valido  check (status in (
        'PENDENTE_VERIFICACAO', 'ATIVO', 'BLOQUEADO', 'ENCERRADO'
    ))
);

create table contas (
    id         bigserial   primary key,
    usuario_id bigint      not null,
    agencia    varchar(4)  not null,
    numero     varchar(12) not null,
    tipo       varchar(16) not null default 'CORRENTE',
    status     varchar(16) not null default 'ATIVA',
    criado_em  timestamptz not null default now(),

    constraint contas_usuario_fk      foreign key (usuario_id) references usuarios (id),
    constraint contas_identificacao   unique (agencia, numero),
    constraint contas_tipo_valido     check (tipo   in ('CORRENTE', 'POUPANCA')),
    constraint contas_status_valido   check (status in ('ATIVA', 'BLOQUEADA', 'ENCERRADA'))
);

create index contas_usuario_idx on contas (usuario_id);
