-- V2 - Credenciais: verificacao de e-mail e refresh tokens rotativos.
--
-- Nenhum token e guardado em claro. O banco so ve o SHA-256 hexadecimal:
-- um vazamento do dump nao vira sessao ativa de ninguem.

create table tokens_verificacao_email (
    id         bigserial   primary key,
    usuario_id bigint      not null,
    token_hash varchar(64) not null,
    expira_em  timestamptz not null,
    usado_em   timestamptz,
    criado_em  timestamptz not null default now(),

    constraint tve_usuario_fk foreign key (usuario_id) references usuarios (id) on delete cascade,
    constraint tve_hash_unico unique (token_hash)
);

create index tve_usuario_idx on tokens_verificacao_email (usuario_id);

-- Rotacao com deteccao de reuso: cada login abre uma "familia". Refresh troca o
-- token e revoga o anterior. Se um token ja revogado reaparecer, foi roubado --
-- a familia inteira cai e o dono precisa entrar de novo.
create table refresh_tokens (
    id          bigserial   primary key,
    usuario_id  bigint      not null,
    token_hash  varchar(64) not null,
    familia     uuid        not null,
    expira_em   timestamptz not null,
    revogado_em timestamptz,
    criado_em   timestamptz not null default now(),

    constraint rt_usuario_fk foreign key (usuario_id) references usuarios (id) on delete cascade,
    constraint rt_hash_unico unique (token_hash)
);

create index rt_familia_idx on refresh_tokens (familia);
create index rt_usuario_idx on refresh_tokens (usuario_id);
