# DIO Spring Boot - Final Project 05: Spring AI (budgeting)

> Versão adaptada por [Ana - AnacLeonel](https://github.com/AnacLeonel), como parte do Desafio de Projeto do Bootcamp Santander/DIO.
> Caminho no repositório: [`05-spring-ai`](https://github.com/AnacLeonel/dio-spring-boot-learning-track/tree/main/05-spring-ai)

## O que o projeto faz

API de orçamento (budgeting) que usa IA para processar comandos em linguagem natural relacionados a transações financeiras. Você diz algo como *"gastei 30 reais na farmácia"* e a IA entende o comando, escolhe a categoria certa e registra a transação no banco de dados — sem formulários, sem campos fixos.

O projeto preserva a mesma arquitetura em camadas (DDD) usada em toda a trilha:

```text
domain/          -> modelo de negócio, regras, contratos
application/     -> use cases, usados tanto pelo REST quanto pela IA
infrastructure/  -> adapters (HTTP, JPA, integração com IA)
```

## Fluxo principal (versão adaptada)

O projeto original (ver seção [Fluxo original](#fluxo-original-com-openai) abaixo) processava **áudio**: transcrição → IA → resposta em voz. Como não tive acesso a créditos pagos da OpenAI, adaptei o fluxo para rodar 100% local e gratuito, mantendo o núcleo do desafio — interpretação de linguagem natural e tool calling — intacto:

1. Cliente envia uma mensagem de **texto** (em vez de áudio).
2. O `ChatClient`, rodando sobre um modelo **Ollama local**, interpreta a intenção.
3. A IA decide qual ferramenta (`@Tool`) chamar.
4. O use case correspondente persiste ou consulta transações no banco **H2 em memória**.
5. A resposta em texto é devolvida ao cliente.

```text
Texto → Ollama (Qwen2.5) → Tool Calling → Use Case → H2 → Resposta em texto
```

## O que foi adaptado e por quê

O desafio original pede créditos de API da OpenAI (Whisper para transcrição, GPT para chat, TTS para voz) e um banco MySQL via Docker. Não tive acesso a nenhum dos dois nesse momento, então adaptei a stack inteira para uma versão gratuita e local, sem perder o objetivo central do desafio: **conectar IA a uma aplicação real, respeitando a arquitetura**.

| Original | Adaptado | Motivo |
|---|---|---|
| OpenAI (GPT-4o-mini) | Ollama local (Qwen2.5:7b) | Sem créditos de API pagos disponíveis |
| Whisper (transcrição de áudio) | Texto direto via `/transactions/ai/text` | Sem provedor de transcrição gratuito equivalente |
| TTS (texto → voz) | Resposta em texto | Mesma limitação acima |
| MySQL via Docker Compose | H2 em memória | Docker Desktop exige virtualização de hardware, indisponível no meu ambiente |

O endpoint original (`/transactions/ai`, com áudio e MP3) foi mantido **comentado e documentado** no código, junto com as configurações necessárias, para deixar claro como restaurar o fluxo completo caso créditos de API estejam disponíveis.

## Bug real encontrado e corrigido

Durante a adaptação, o tool calling falhava consistentemente com `NullPointerException`, mesmo com prompts bem instruídos e testando dois modelos diferentes (Llama 3.2 3B e Qwen2.5 7B) — o que descartou hipótese de limitação do modelo e apontou para um problema estrutural.

**Causa raiz:** a ferramenta `persist-transaction` recebia um único parâmetro do tipo `record` (`PersistTransactionInput`). O Spring AI (versão milestone `2.0.0-M4`) falhava ao desserializar esse record a partir da chamada de function calling do Ollama, sempre retornando `input = null`.

**Correção:** reestruturei o método anotado com `@Tool` para receber parâmetros simples e nomeados (`String description, long amount, Category category`) em vez do record composto, mantendo uma sobrecarga interna que preserva o uso do record no restante do código. Isso eliminou o erro por completo, em ambos os modelos testados.

```java
// Antes (falhava)
@Tool(name = "persist-transaction", description = "...")
public TransactionOutput execute(PersistTransactionInput input) { ... }

// Depois (funciona)
@Tool(name = "persist-transaction", description = "...")
public TransactionOutput execute(
        @ToolParam(description = "Descrição do gasto") String description,
        @ToolParam(description = "Valor do gasto (em centavos)") long amount,
        @ToolParam(description = "Categoria de uma transação") Category category) {
    return execute(new PersistTransactionInput(description, amount, category));
}
```

## Melhorias implementadas

Além da adaptação de infraestrutura, implementei três melhorias sobre o projeto base:

### 1. Validação de negócio
`Transaction` agora rejeita valores de `amount` menores ou iguais a zero diretamente no domínio — a regra vale para qualquer forma de criar uma transação (REST tradicional ou IA), não só numa camada específica.

### 2. Tratamento de erro global
Um `GlobalExceptionHandler` (`@RestControllerAdvice`) captura exceções de validação e devolve `400 Bad Request` com uma mensagem estruturada, em vez do `500 Internal Server Error` genérico que a aplicação retornava antes.

```json
{
  "timestamp": "2026-08-20T21:33:10.684Z",
  "status": 400,
  "error": "Bad Request",
  "message": "O valor da transação deve ser maior que zero"
}
```

### 3. Nova ferramenta de IA: consulta de total gasto
Adicionei a tool `get-total-spent`, que permite perguntar em linguagem natural *"quanto eu gastei no total?"* e receber o valor somado de todas as transações registradas, convertido de centavos para reais.

## Tecnologias usadas

- Java 26 (Oracle JDK)
- Spring Boot 4.0.5
- Spring AI 2.0.0-M4 (ChatClient, Tool Calling, Ollama starter)
- Ollama + Qwen2.5:7b (modelo local)
- H2 Database (em memória)
- Gradle

## Como executar

### Pré-requisitos

- Java 26 instalado
- [Ollama](https://ollama.com/download) instalado e rodando
- Modelo baixado: `ollama pull qwen2.5:7b`

### Rodando a aplicação

```bash
cd 05-spring-ai
./gradlew bootRun
```

A aplicação sobe em `http://localhost:8080`, usando H2 em memória (sem necessidade de banco externo ou Docker).

### Testando o fluxo principal

**Criar uma transação via REST tradicional:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/transactions" -Method Post -ContentType "application/json" -Body '{"description":"Mercado","amount":5000,"category":"GROCERIES"}'
```

**Criar uma transação via IA (linguagem natural):**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/transactions/ai/text" -Method Post -ContentType "application/json" -Body '{"message":"gastei 30 reais na farmacia"}'
```

**Consultar transações por categoria:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/transactions/PHARMA" -Method Get
```

**Perguntar o total gasto via IA:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/transactions/ai/text" -Method Post -ContentType "application/json" -Body '{"message":"quanto eu gastei no total?"}'
```

## Fluxo original (com OpenAI)

O fluxo com áudio completo (transcrição + chat + texto-para-voz) está documentado e comentado dentro de `TransactionController.java`. Para restaurá-lo com créditos da OpenAI disponíveis:

1. Reverter `spring-ai-starter-model-ollama` para `spring-ai-starter-model-openai` no `build.gradle`.
2. Configurar `OPENAI_API_KEY` e as propriedades de transcrição/TTS no `application.properties` (removidas nesta versão, ver histórico do repositório).
3. Descomentar o endpoint `/transactions/ai` (com `TranscriptionModel` e `TextToSpeechModel`) no `TransactionController`.

## O que eu aprendi

- Como funciona o **tool calling** do Spring AI na prática: o modelo decide *quando* chamar uma ferramenta, mas a *forma como os parâmetros são estruturados* no código Java afeta diretamente se essa chamada terá sucesso.
- Que nem todo erro de IA é "culpa do modelo" — testar com dois modelos diferentes (Llama 3.2 e Qwen2.5) e ver o mesmo erro persistir foi o que me fez procurar a causa no framework, não no prompt.
- Diferença prática entre modelos leves e robustos em tarefas de function calling, e por que a escolha do modelo importa tanto quanto a engenharia de prompt.
- Como adaptar uma stack de IA para rodar localmente e gratuitamente, mantendo a arquitetura em camadas (DDD) intacta — a troca de OpenAI para Ollama não exigiu nenhuma mudança na camada de domínio ou nos use cases, só na infraestrutura.
- Como estruturar validações de negócio no domínio e tratamento de erros de forma global no Spring.

## Referências de arquitetura compartilhada

Os conceitos de arquitetura usados neste módulo são documentados no README raiz da trilha:

- [Camadas DDD](../README.md#ddd-layered-architecture)
- [Class vs record](../README.md#java-class-vs-java-record-in-domain-modeling)
- [Identificadores fortemente tipados](../README.md#strong-typed-identifiers)
- [Repository pattern](../README.md#repository-pattern)
- [Use cases e Clean Architecture](../README.md#use-cases-and-clean-architecture)

## Documentação do Spring AI

- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/index.html)
- [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Tools API](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Ollama Chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)