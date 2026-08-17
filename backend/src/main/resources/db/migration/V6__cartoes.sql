-- V6 - Cartao virtual.
--
-- Um cartao por conta. O numero e gerado com digito de Luhn, o mesmo algoritmo
-- que as bandeiras usam: assim ele passa em qualquer validador de formulario,
-- o que torna a demonstracao honesta em vez de um numero inventado.
--
-- Nao ha dinheiro real por tras: e um cartao de demonstracao e nada nele
-- funciona fora deste sistema.

create table cartoes (
    id        bigserial   primary key,
    conta_id  bigint      not null,
    numero    varchar(16) not null,
    cvv       varchar(4)  not null,
    titular   varchar(120) not null,
    validade  date        not null,
    status    varchar(16) not null default 'ATIVO',
    criado_em timestamptz not null default now(),

    constraint cartoes_conta_fk    foreign key (conta_id) references contas (id),
    constraint cartoes_conta_unica unique (conta_id),
    constraint cartoes_numero_unico unique (numero),
    constraint cartoes_status_valido check (status in ('ATIVO', 'BLOQUEADO', 'CANCELADO'))
);
