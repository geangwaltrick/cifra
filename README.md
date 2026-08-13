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
| `./mvnw test`   | 22 testes de unidade | não          |
| `./mvnw verify` | 22 unidade + 53 integração | sim    |

Os quatro que sustentam a tese do projeto:

| Cenário | O que prova |
|---|---|
| 50 saques paralelos sobre R$ 100 | exatamente 10 passam; saldo fecha em zero |
| 50 saídas paralelas sobre teto de R$ 5.000 | exatamente 25 passam; limite não fura |
| 50 transferências cruzadas A↔B | nenhuma falha por deadlock; total preservado |
| 50 depósitos com a mesma chave | uma transação só; todas devolvem o mesmo id |

Testes de integração sobem um PostgreSQL 16 real via Testcontainers — a mesma
versão do `docker-compose.yml`. Testar contra H2 provaria a coisa errada.

## API disponível

| Método | Rota                                | Autenticada | Observação                              |
|--------|-------------------------------------|-------------|-----------------------------------------|
| POST   | `/api/v1/auth/registro`             | não         | Valida CPF e já abre a conta corrente    |
| GET    | `/api/v1/auth/verificar-email`      | não         | Ativa o usuário pelo link do e-mail      |
| POST   | `/api/v1/auth/login`                | não         | 5 tentativas por 15 min, por e-mail e IP |
| POST   | `/api/v1/auth/refresh`              | não         | Rotaciona e detecta reuso                |
| GET    | `/api/v1/contas/me`                 | sim         | Conta do usuário do token                |
| GET    | `/api/v1/contas/me/saldo`           | sim         | Projeção **e** soma do razão, lado a lado |
| POST   | `/api/v1/depositos`                 | sim         | Aceita `Idempotency-Key`                 |
| POST   | `/api/v1/saques`                    | sim         | Aceita `Idempotency-Key`                 |
| POST   | `/api/v1/transferencias`            | sim         | Por agência + número; `Idempotency-Key`  |
| POST   | `/api/v1/pix/chaves`                | sim         | CPF, e-mail, telefone ou aleatória        |
| GET    | `/api/v1/pix/chaves`                | sim         | Chaves da conta                          |
| DELETE | `/api/v1/pix/chaves/{id}`           | sim         | Remove uma chave                         |
| POST   | `/api/v1/pix/transferencias`        | sim         | Pagamento por chave                      |
| GET    | `/api/v1/contas/me/extrato`         | sim         | Paginado; filtros `de`, `ate`, `tipo`    |
| GET    | `/api/v1/contas/me/limite`          | sim         | Teto, gasto de hoje e disponível         |
| PUT    | `/api/v1/contas/me/limite`          | sim         | Ajusta o teto diário                     |
| POST   | `/api/v1/transacoes/{id}/estorno`   | sim         | Transação simétrica                      |
| POST   | `/api/v1/seguranca/senha-transacional` | sim      | Define a senha de movimentação           |

Saídas de dinheiro aceitam o cabeçalho `X-Senha-Transacional`, exigido a partir
do momento em que o titular define uma.

Erros seguem RFC 7807. O campo `type` é estável e é nele que o front decide o
comportamento — nunca no texto da mensagem:

```json
{
  "type": "https://cifra.dev/problemas/credenciais-invalidas",
  "title": "credenciais-invalidas",
  "status": 401,
  "detail": "E-mail ou senha incorretos.",
  "momento": "2026-08-13T17:22:07Z"
}
```

### Decisões de segurança

- **Senha em BCrypt custo 12** (~250 ms por hash). Caro de propósito.
- **Tokens nunca em claro no banco.** Verificação de e-mail e refresh guardam só o SHA-256; um dump vazado não vira sessão ativa.
- **Refresh rotativo com detecção de reuso.** Cada login abre uma família; cada refresh revoga o anterior. Se um token revogado reaparece, ele foi copiado — e a família inteira cai. A revogação roda em `REQUIRES_NEW` porque a exceção que a acompanha faria rollback dela.
- **Login não revela se o e-mail existe.** Mesma resposta e mesmo custo de tempo nos dois casos: quando o e-mail não existe, um hash descartável é comparado assim mesmo, senão o tempo de resposta viraria um oráculo.

### Dinheiro trafega como texto no JSON

```json
{ "saldo": "374.75", "saldoConferidoNoRazao": "374.75" }
```

Número em JSON vira `double` na maioria dos clientes — em JavaScript, sempre.
Um saldo de `0.10` serializado como número volta do `JSON.parse` como ponto
flutuante binário, e daí em diante toda conta feita no front carrega erro.
String obriga o cliente a escolher conscientemente como representar o valor.
Registrado como módulo Jackson global, não anotação por campo: um DTO novo com
valor monetário não pode depender de alguém lembrar.

### De onde vem o dinheiro de um depósito

Se toda transação precisa somar zero, um depósito não pode ter um lançamento só.
O contrapeso é a **conta de liquidação**: a fronteira contábil entre o Cifra e o
mundo externo. Depositar credita o cliente e debita a liquidação; sacar faz o
contrário.

Consequência útil: o saldo da liquidação é, a qualquer instante, o simétrico
exato da soma de todas as contas de cliente — uma segunda forma de conferir os
livros, independente da primeira.

**Trade-off assumido:** toda entrada e saída de dinheiro contende na mesma linha
de saldo da liquidação, que vira ponto de serialização. Para uma demo é o
correto — é o que garante a conta fechar. Em volume real, o caminho é
particionar a liquidação em várias contas e somá-las.

### Concorrência

O `SELECT ... FOR UPDATE` trava as contas **sempre em ordem crescente de id**.
Sem isso, uma transferência de A para B simultânea a uma de B para A trava cada
uma a sua primeira conta e as duas esperam para sempre. Ordenando, o ciclo não
se forma — o deadlock é eliminado por construção, não tratado depois.

O banco ainda tem a última palavra: `check (permite_negativo or saldo >= 0)`. Se
algum caminho de código escapar da validação, o Postgres recusa.

### O limite diário é checado depois do lock

Parece detalhe de ordenação e não é. Se a soma do que já saiu hoje fosse lida
antes do `SELECT ... FOR UPDATE`, duas transferências simultâneas de R$ 3.000
sobre um teto de R$ 5.000 leriam ambas "nada gasto hoje", ambas passariam, e o
dia fecharia em R$ 6.000. Com o lock já em mãos, a segunda só lê depois que a
primeira gravou. Há um teste que dispara 50 saídas ao mesmo tempo e exige que
exatamente 25 passem.

### Estorno é lançamento novo, nunca `DELETE`

Estornar escreve lançamentos de sinal contrário e marca a original como
`ESTORNADA`. A transação original continua no razão, o histórico permanece
auditável e a soma segue zero. Um estorno que deixaria a conta negativa — porque
o destinatário já gastou o que recebeu — é recusado: forçá-lo seria inventar
dinheiro.

### Extrato com saldo corrente

A window function roda sobre o histórico **inteiro** da conta, na subconsulta
interna; os filtros de período e tipo entram só do lado de fora. Invertida a
ordem, o saldo corrente do primeiro dia do recorte começaria do zero em vez de
partir do que a conta já tinha — e o extrato mostraria números que nunca
existiram. Filtrar por `tipo=SAQUE` num histórico de R$ 1.000 menos R$ 250
devolve `saldoApos: 750.00`, não `-250.00`.

### Duas senhas, dois riscos diferentes

A senha de movimentação é separada da de acesso e opcional até o titular
definir. O cenário que ela cobre é concreto: token de acesso vazado não basta
para esvaziar a conta. Ler saldo e extrato continua só com o token; mover
dinheiro exige algo que o atacante não capturou junto. Trocá-la exige provar a
senha de acesso — senão o token vazado bastaria para definir a segunda senha e
contornar exatamente a proteção que ela dá.

### Reconciliação

Um job pergunta a cada 10 minutos, e o resultado esperado é sempre nada:

1. alguma transação tem lançamentos que não somam zero?
2. algum saldo projetado divergiu da soma do razão?
3. a soma de todo o razão deixou de ser zero?

O resultado é exposto em `/actuator/health` como o componente `razao`. A saúde
da aplicação inclui uma pergunta contábil: **os livros fecham?** Se pararem de
fechar, o monitoramento acusa junto com banco fora do ar — que é mais ou menos
a gravidade do problema.

### O front: três decisões que importam mais que as telas

- **Refresh em voo único.** Cinco requisições que expiram juntas disparariam cinco refreshes. O backend rotaciona e revoga o anterior a cada um, então o segundo chega com token já revogado, o servidor lê como roubo e derruba a família — o usuário seria deslogado por ter aberto duas abas. Quem chega durante uma renovação em andamento espera nela.
- **A chave de idempotência nasce com a intenção de pagar**, não com a requisição, e é reusada no retry. Se mudasse a cada envio, um reenvio após timeout viraria um segundo pagamento e a proteção do backend não teria o que proteger.
- **Dinheiro nunca vira `number`.** Contas em centavos inteiros, resultado de volta para string. Um `parseFloat` jogaria fora a decisão tomada no backend.

O texto de erro vem do `type` do RFC 7807, nunca do `detail` — um `type` desconhecido cai em mensagem genérica em vez de vazar vocabulário de servidor.

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```

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
- [x] **01 — Identidade:** cadastro com validação de CPF, JWT, abertura de conta
- [x] **02 — Razão:** lançamentos, idempotência, lock ordenado, reconciliação
- [x] **03 — Produto:** chaves PIX, extrato, limites, estorno, auditoria
- [x] **04 — Front:** React + Vite + TypeScript
- [ ] **05 — Publicação:** deploy, conta demo com reset diário
