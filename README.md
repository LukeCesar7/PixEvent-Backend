<p align="center">
  <img src="https://github.com/LukeCesar7/PixEvent-Backend/blob/main/hero.png?raw=true" alt="PixEvent" width="100%">
</p>

<h1 align="center">PixEvent</h1>

<p align="center">
  API REST para venda de ingressos e reserva de mesas em eventos, com pagamento via PIX,
  ingresso digital com QR Code e validação na portaria.
</p>

<p align="center">
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 3.3" src="https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white">
  <img alt="PostgreSQL 16" src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white">
</p>

---

## Sumário

- [Sobre o projeto](#sobre-o-projeto)
- [Funcionalidades](#funcionalidades)
- [Como funciona o fluxo de um pedido](#como-funciona-o-fluxo-de-um-pedido)
- [Stack](#stack)
- [Arquitetura](#arquitetura)
- [Rodando o projeto](#rodando-o-projeto)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Referência da API](#referência-da-api)
- [Segurança](#segurança)
- [Limitações conhecidas e próximos passos](#limitações-conhecidas-e-próximos-passos)

---

## Sobre o projeto

O PixEvent é o backend de uma plataforma de bilheteria para eventos presenciais. Ele cuida de
todo o ciclo: o cliente monta o pedido (ingressos e/ou mesas), paga por PIX, a organização
confirma o pagamento, o sistema gera um **ingresso com QR Code único** e, no dia do evento, a
portaria valida cada QR Code uma única vez.

O projeto nasceu em Node.js/Express/SQLite e foi portado para **Spring Boot + PostgreSQL**
mantendo o mesmo contrato de API (rotas e formato dos JSONs).

## Funcionalidades

- **Pedidos** de ingresso e mesa, com cálculo de valor no servidor (o cliente nunca envia preço).
- **Mapa de mesas** com status em tempo real (`disponivel` / `reservada`) e reserva atômica
  junto com o pedido: se algo falhar, nada fica reservado.
- **Ingresso digital**: geração de QR Code com token aleatório, com opção de regenerar o token.
- **Validação na portaria**: o QR Code só é aceito uma vez; tentativas repetidas informam quem
  e quando entrou.
- **Painel administrativo**: métricas, busca e listagem paginada de pedidos, confirmação manual
  de pagamento, cancelamento (libera as mesas), edição e lista de entradas.
- **E-mail transacional** com o ingresso (SendGrid).
- **Autenticação** do admin por token assinado (HMAC-SHA256) e **rate limiting** por IP.
- **Backup** opcional do banco na inicialização (`pg_dump`).

## Como funciona o fluxo de um pedido

```text
Cliente                     API                          Admin / Portaria
   │  POST /api/pedido       │                                   │
   ├────────────────────────►│ valida itens, calcula valor,      │
   │                         │ reserva mesas (transação)         │
   │◄────────────────────────┤ status: aguardando_pix            │
   │                         │                                   │
   │  paga via PIX e envia   │                                   │
   │  comprovante (WhatsApp) │                                   │
   │                         │   POST /api/admin/pedidos/{id}/confirmar
   │                         │◄──────────────────────────────────┤
   │                         │ status: pago + gera QR Code       │
   │◄── e-mail com ingresso ─┤                                   │
   │                         │                                   │
   │       (no evento)       │   POST /api/scanner/validar       │
   │                         │◄──────────────────────────────────┤
   │                         │ valido: true  (QR marcado como usado)
```

Status possíveis de um pedido: `pendente`, `aguardando_pix`, `pago`, `cancelado`, `expirado`.

**Regras de negócio atuais:**

| Regra | Valor |
|---|---|
| Preço do ingresso | R$ 30,00 |
| Preço da mesa | R$ 100,00 |
| Máximo de mesas por pedido | 3 |
| Itens por pedido | até 10 linhas, quantidade de 1 a 400 |

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Mail) |
| Banco de dados | PostgreSQL 14+ |
| Mapeamento DTO ↔ entidade | MapStruct 1.6 |
| Boilerplate | Lombok |
| Build | Maven 3.9+ |
| Containers | Docker e Docker Compose |

## Arquitetura

Aplicação em camadas, num único módulo:

```text
src/main/java/com/pixevent/
├── controller/   # Endpoints REST (Admin, Pedido, Mesa, Scanner, Webhook)
├── service/      # Regras de negócio (Pedido, Mesa, Rifa, Email, Backup)
├── repository/   # Spring Data JPA
├── entity/       # Entidades JPA e enums (com conversores para o banco)
├── dto/          # Objetos de entrada e saída da API
├── mapper/       # Mappers MapStruct (entidade → DTO)
├── security/     # Autenticação do admin e rate limiting
├── config/       # CORS e interceptors
├── exception/    # ApiException + GlobalExceptionHandler
└── util/         # Token, QR Code e validações
```

Decisões que vale conhecer:

- **As respostas da API usam DTOs**, nunca entidades JPA. O mapa público de mesas
  (`GET /api/mesas`) expõe só número e status; dados como `pedidoId` ficam restritos ao admin.
- **Erros centralizados**: `ApiException` + `GlobalExceptionHandler` padronizam as respostas de erro.
- **Reserva de mesas transacional**: criar o pedido e reservar as mesas acontece na mesma
  transação.
- **Validação do QR Code atômica**: a marcação como "usado" é um `UPDATE` condicional, o que
  impede que duas leituras simultâneas do mesmo QR Code entrem.

## Rodando o projeto

### Requisitos

- Docker e Docker Compose **ou**
- Java 17+, Maven 3.9+ e PostgreSQL 14+
- (Opcional) `pg_dump` no PATH, para o backup automático na inicialização

### Opção 1 — Docker Compose (recomendada)

```bash
# 1. copie o arquivo de exemplo e preencha com valores reais
cp .env.example .env

# 2. suba o banco e a aplicação
docker compose up --build
```

A API fica disponível em `http://localhost:3001`. `ADMIN_PASSWORD` e `JWT_SECRET` são
obrigatórios: o Compose não sobe sem eles.

Gere valores seguros assim:

```bash
openssl rand -base64 18   # ADMIN_PASSWORD
openssl rand -hex 32      # JWT_SECRET
```

### Opção 2 — Local com Maven

```bash
# cria o banco (se ainda não existir)
createdb -U postgres pixevent

export DB_HOST=localhost DB_PORT=5432 DB_NAME=pixevent DB_USER=pixevent DB_PASSWORD=troque-isto
export ADMIN_PASSWORD=$(openssl rand -base64 18)
export JWT_SECRET=$(openssl rand -hex 32)

mvn spring-boot:run
```

Ou gerando o jar:

```bash
mvn clean package
java -jar target/pixevent-backend.jar
```

As tabelas são criadas automaticamente na primeira execução (`ddl-auto=update`).
O arquivo `src/main/resources/schema-reference.sql` serve de referência caso você prefira
rodar o schema manualmente e usar `JPA_DDL_AUTO=validate` em produção.

### Cadastrando as mesas

O mapa de mesas vem da tabela `mesas`, que precisa ser populada antes de aceitar reservas.
Exemplo para criar as mesas 1 a 50:

```sql
INSERT INTO mesas (numero)
SELECT generate_series(1, 50)::text;
```

### Verificando

```bash
curl http://localhost:3001/api/mesas
```

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `3001` | Porta da API |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | Endereço do PostgreSQL |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `pixevent` / `pixevent` / — | Credenciais do banco |
| `JPA_DDL_AUTO` | `update` | Use `validate` em produção |
| `ADMIN_PASSWORD` | — | Senha do painel admin **(obrigatória)** |
| `JWT_SECRET` | — | Segredo de assinatura do token **(obrigatório)** |
| `JWT_EXPIRES_IN` | `8h` | Validade do token do admin |
| `ALLOW_LEGACY_ADMIN_SECRET` | `false` | Habilita o header legado `x-admin-secret` |
| `BASE_URL` | `http://localhost:3001` | URL pública da aplicação |
| `CORS_ORIGINS` | vazio | Origens permitidas (use o domínio real em produção) |
| `SENDGRID_API_KEY` | vazio | Chave do SendGrid para envio de e-mails |
| `EMAIL_FROM` / `EMAIL_FROM_NAME` | `noreply@example.com` / `PixEvent` | Remetente dos e-mails |
| `WA_NUMERO` | vazio | Número de WhatsApp para envio do comprovante |
| `RATE_LIMIT_API` | `120` | Requisições por janela, rotas gerais |
| `RATE_LIMIT_LOGIN` | `8` | Requisições por janela, login |
| `RATE_LIMIT_PEDIDO` | `20` | Requisições por janela, criação de pedido |
| `RATE_LIMIT_SCANNER` | `80` | Requisições por janela, scanner |
| `BACKUP_ON_START` | `true` (`false` no Compose) | Roda `pg_dump` ao iniciar |
| `BACKUP_DIR` | `./backups` | Pasta de destino dos backups |

## Referência da API

### Público

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/mesas` | Lista as mesas com número e status |
| `POST` | `/api/pedido` | Cria um pedido (ingressos e/ou mesas) |
| `GET` | `/api/pedido/{id}/status` | Consulta o status de um pedido |

### Portaria

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/scanner/validar` | Valida e consome um QR Code (`{"token": "..."}`) |

### Administração

O login retorna um token que deve ser enviado no header `Authorization: Bearer <token>`.

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/admin/login` | Autentica e retorna o token |
| `GET` | `/api/admin/metricas` | Contagem por status e situação das mesas |
| `GET` | `/api/admin/pedidos` | Lista paginada (filtros `status`, `nome`, `page`) |
| `GET` | `/api/admin/pedidos/pagos` | Pedidos pagos |
| `GET` | `/api/admin/pedidos/{id}` | Detalhe de um pedido |
| `POST` | `/api/admin/pedidos` | Cria um pedido manualmente |
| `PUT` | `/api/admin/pedidos/{id}` | Edita um pedido |
| `DELETE` | `/api/admin/pedidos/{id}` | Remove um pedido |
| `POST` | `/api/admin/pedidos/{id}/confirmar` | Confirma o pagamento e gera o ingresso |
| `POST` | `/api/admin/pedidos/{id}/cancelar` | Cancela e libera as mesas |
| `GET` | `/api/admin/pedidos/{id}/ingresso` | Reexibe o QR Code de um pedido pago |
| `POST` | `/api/admin/pedidos/{id}/ingresso/regenerar` | Gera um novo token e QR Code |
| `GET` | `/api/admin/entradas` | Pedidos cujo QR Code já foi usado |
| `GET` | `/api/pedido/buscar?nome=` | Busca pedidos por nome |
| `POST` | `/api/pedido/{id}/confirmar` | Confirmação manual de pagamento |

### Exemplo: criando um pedido

```bash
curl -X POST http://localhost:3001/api/pedido \
  -H "Content-Type: application/json" \
  -d '{
        "nome": "Maria Silva",
        "cpf": "00000000000",
        "email": "maria@example.com",
        "itens": [
          { "produto": "ingresso", "quantidade": 2 },
          { "produto": "mesa", "quantidade": 1 }
        ]
      }'
```

> Os nomes exatos dos campos de entrada estão em `dto/PedidoRequest.java`.
> Uma coleção do Postman pode ser mantida na pasta `docs/` para facilitar os testes.

## Segurança

- Troque `ADMIN_PASSWORD` e `JWT_SECRET` por valores novos antes de publicar. Nunca reaproveite
  os de ambientes de teste.
- Configure `CORS_ORIGINS` com o domínio real. Não deixe em branco nem use `*` em produção.
- Mantenha `ALLOW_LEGACY_ADMIN_SECRET=false`, a menos que precise do header `x-admin-secret`.
- O arquivo `.env` não deve ir para o Git (já está no `.gitignore`).
- O rate limiting é em memória, por IP, e funciona para uma única instância.

## Limitações conhecidas e próximos passos

- **Webhook do Mercado Pago** (`POST /webhook/mp`): hoje só responde `200 OK`, sem processar o
  payload. A confirmação de pagamento é manual pelo admin.
- **Rate limiting** em memória: ao escalar horizontalmente, trocar por estado compartilhado
  (Redis + Bucket4j).
- **Rifas**: o serviço de geração de números existe, mas ainda não está ligado ao fluxo de pedidos.
- **Backup por `pg_dump`**: em produção, prefira o backup gerenciado do provedor de Postgres e
  deixe `BACKUP_ON_START=false`.
- **Testes automatizados**: ainda não há suíte de testes. Um bom começo são testes de integração
  para o fluxo criar pedido → confirmar → validar QR Code.
