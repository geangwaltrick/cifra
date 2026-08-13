# Cifra

Um banco digital construído sobre um razão de partidas dobradas.

> **Projeto educacional.** Não movimenta dinheiro real, não integra com o SPI do
> Banco Central e o seed não contém nenhum dado pessoal verdadeiro.

---

## A decisão que define o projeto

Quase todo projeto de banco em portfólio resolve uma transferência assim:

```sql
update contas set saldo = saldo - 100 where id = 1;
update contas set saldo = saldo + 100 where id = 2;
```

Funciona na demo e não sobrevive a nada: não há histórico, não há como provar que
o dinheiro que saiu de um lugar entrou em outro, e duas requisições simultâneas
furam o saldo.

No Cifra **o saldo não é um dado primário** — ele é a soma dos lançamentos do
razão. Toda movimentação grava uma transação com dois lançamentos assinados que
somam exatamente zero:

| Lançamento | Conta           | Titular         | Valor (BRL) |
|-----------:|-----------------|-----------------|------------:|
|      #8912 | 0001 / 47201-3  | Ana Ribeiro     |     −100,00 |
|      #8913 | 0001 / 51884-0  | Bruno Nakamura  |     +100,00 |
|            |                 | **Soma**        |    **0,00** |

Esse zero é o invariante do sistema, e existe um teste que o exige do banco
inteiro. Extrato, auditoria, estorno e reconciliação saem como consequência do
modelo, não como funcionalidades separadas.

## Os quatro pilares

1. **Partidas dobradas** — lançamentos assinados, append-only, somando zero por transação.
2. **Idempotência** — `Idempotency-Key` garantida por *unique constraint* no banco, não por um `if`.
3. **Concorrência** — lock pessimista com ordenação determinística das contas; deadlock eliminado por construção.
4. **Estado e estorno** — transação é máquina de estados; estorno é lançamento novo, jamais `DELETE`.

## Stack

| Camada     | Escolha                                              |
|------------|------------------------------------------------------|
| Linguagem  | Java 25                                               |
| Framework  | Spring Boot 4.1 (WebMVC, Data JPA, Actuator)          |
| Banco      | PostgreSQL 16                                         |
| Migrations | Flyway — `ddl-auto=validate`, o Hibernate nunca cria nada |
| Testes     | JUnit 5 + AssertJ + Testcontainers (Postgres real)    |
| CI         | GitHub Actions                                        |

## Rodando localmente

**Pré-requisitos:** JDK 25 e Docker. O Maven não precisa ser instalado — o
wrapper (`mvnw`) baixa a versão correta sozinho.

```bash
# 1. sobe Postgres e o capturador de e-mails
docker compose up -d

# 2. build + testes unitários + testes de integração
cd backend
./mvnw verify

# 3. sobe a aplicação
./mvnw spring-boot:run
```

Verificação rápida:

```bash
curl http://localhost:8080/actuator/health
```

| Serviço              | Endereço                              |
|----------------------|---------------------------------------|
| API                  | http://localhost:8080                 |
| Saúde                | http://localhost:8080/actuator/health |
| Caixa de e-mail (dev)| http://localhost:8025                 |
| PostgreSQL           | `localhost:5432` — `cifra` / `cifra`  |

## Suítes de teste

As duas suítes são separadas de propósito:

| Comando         | Roda            | Precisa de Docker |
|-----------------|-----------------|-------------------|
| `./mvnw test`   | `*Test` (unidade) | não             |
| `./mvnw verify` | `*Test` + `*IT` (integração) | sim  |

Testes de integração sobem um PostgreSQL 16 real via Testcontainers — a mesma
versão do `docker-compose.yml`. Testar contra H2 provaria a coisa errada.

## Estrutura

```
cifra/
├── backend/                  # API Spring Boot
│   └── src/main/resources/
│       └── db/migration/     # todo o schema vive aqui
├── .github/workflows/ci.yml
└── docker-compose.yml
```

## Roadmap

- [x] **00 — Fundação:** wrapper, Flyway, Testcontainers, CI
- [ ] **01 — Identidade:** cadastro com validação de CPF, JWT, abertura de conta
- [ ] **02 — Razão:** lançamentos, idempotência, lock ordenado, reconciliação
- [ ] **03 — Produto:** chaves PIX, extrato, limites, estorno, auditoria
- [ ] **04 — Front:** React + Vite + TypeScript
- [ ] **05 — Publicação:** deploy, conta demo com reset diário
