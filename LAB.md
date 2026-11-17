# secure-tasks-api

Laboratório didático sobre **autorização por objeto (BOLA / IDOR, OWASP API1:2023)** em uma API Spring Boot, corrigido com **Spec-Driven Development no Kiro**. Preparado para o meetup AWS User Group (awsugtm).

> Uso exclusivo local. HTTP Basic, usuários em memória e senhas fictícias existem só para demonstração. Não use esta configuração em ambientes reais.

## Versões do lab

| Tag | Commit | O que é |
|---|---|---|
| `lab-vulneravel` | `8d185e6` | Baseline com a falha BOLA proposital |
| `lab-nao-vulneravel` | `bfec583` | Versão corrigida |

```bash
git checkout lab-vulneravel       # versão vulnerável
git checkout lab-nao-vulneravel   # versão corrigida
```

## A falha

Na versão vulnerável, `GET /api/tasks/{id}` não usava o usuário autenticado e buscava só por id (`repository.findById(id)`). Qualquer usuário autenticado lia a tarefa de outro:

```text
bob lê a tarefa 42 de alice  ->  200 {"id":42,"title":"Revisar relatório trimestral (fictício)"}
```

## A correção

- `TaskRepository.findByIdAndOwner(id, owner)`: a verificação de dono acontece na própria consulta, sem janela entre buscar e autorizar.
- O username vem só de `principal.getName()` (Spring Security). O controller não lê parâmetros, headers nem corpo do cliente.
- Tarefa de outro dono e id inexistente retornam o **mesmo 404**, para não revelar a existência da tarefa.
- `findByIdVulnerable` foi removido.
- A API retorna só DTOs (`TaskResponse` com `id` e `title`). A entidade `Task` nunca é serializada.
- `TaskIdParser` valida o id como inteiro decimal dentro do intervalo de `Long` (400 se inválido).
- `ApiExceptionHandler` devolve corpos de erro fixos, sem `path`, `timestamp`, id nem dados da tarefa.
- Logs registram só `tarefa nao encontrada id=<n>`, sem username, dono, título nem `Authorization`.

Não foram alterados: `SecurityConfig`, `application.properties` e `DataSeeder`.

## Comportamento esperado

| Requisição | Resultado |
|---|---|
| Dono consulta a própria tarefa | 200 com `id` e `title` |
| Outro usuário autenticado consulta a tarefa | 404 |
| Id inexistente | 404, idêntico ao caso anterior |
| Sem credenciais ou credenciais inválidas | 401 |
| Id inválido (`abc`, `1.5`, `0x1F`, overflow) | 400 genérico, sem eco do valor |
| `GET /api/tasks` | 200, só as tarefas do usuário |

## Como rodar

Requisitos: Java 21 e Maven. A aplicação escuta apenas em `127.0.0.1:8080`, com H2 em memória.

```powershell
mvn spring-boot:run
```

Dados fictícios: tarefa 42 (alice) e tarefa 43 (bob). Usuários: `alice` / `alice-demo` e `bob` / `bob-demo`.

```powershell
curl -u bob:bob-demo   http://127.0.0.1:8080/api/tasks/42    # 404 (na versão vulnerável: 200)
curl -u bob:bob-demo   http://127.0.0.1:8080/api/tasks/999   # 404, idêntico ao anterior
curl -u alice:alice-demo http://127.0.0.1:8080/api/tasks/42  # 200
curl http://127.0.0.1:8080/api/tasks/42                      # 401
```

## Testes

```powershell
mvn test
```

A suíte tem **72 testes**, todos passando na versão corrigida (execução local, última rodada completa):

- **Regressão:** `bobAcessaTarefa42DeAlice_retorna404`, que substituiu o teste de caracterização que esperava 200. Na versão vulnerável ele falha com `expected:<404> but was:<200>`.
- **Positivos e negativos de autorização:** dono, não dono, id inexistente, sem credenciais, credenciais inválidas e owner enviado por query ou header (ignorado).
- **Unitários e de repositório:** `TaskService` com Mockito e `findByIdAndOwner` sobre H2.
- **Logs:** sem senha, `Authorization` nem PII.
- **Fumaça:** usuários, seeder e remoção do método vulnerável.
- **Propriedade (jqwik):** 9 propriedades, com 100 a 200 tentativas cada.

Os testes usam `httpBasic` contra a configuração real de segurança. Não há `@WithMockUser`, `@Disabled` nem CSRF desabilitado.

## Como foi feito (Kiro)

1. **Regras de segurança** em `.kiro/steering/security.md`: autorização pela identidade autenticada, nunca confiar em owner do cliente, DTOs, sem PII em logs, testes positivos e negativos, sem deploy e sem serviços externos.
2. **Spec** em `.kiro/specs/secure-task-access/`: `requirements.md` (13 requisitos), `design.md` (fluxo de identidade, limites de confiança e 9 propriedades de corretude) e `tasks.md`.
3. **Ordem das tasks:** teste de regressão, demonstrar a falha, correção mínima, repetir os testes e revisar o diff.
4. **Aprovação humana** em cada fase e antes de cada comando de build ou mudança de segurança.

### O que o jqwik encontrou

O `TaskIdParser` rejeitava ids com mais de 20 caracteres, mesmo sendo decimais válidos. O jqwik reduziu o contraexemplo para `-00000000000000001000`. Os testes de exemplo não cobriam isso. A correção removeu o atalho de tamanho e deixou o `Long.parseLong` decidir o overflow.

### Outros achados do design

- O corpo de erro padrão do Spring Boot inclui `path` e `timestamp`, e o `path` contém o id. Isso quebraria a igualdade entre o 404 de tarefa alheia e o de id inexistente.
- O conversor `String` para `Long` do Spring aceita hexadecimal e octal, o que violava o requisito de decimal estrito.

## Revisão de segurança

Sem achados críticos, altos ou médios. Itens baixos e informativos:

- `IllegalArgumentException` vira 400 de forma ampla (`ApiExceptionHandler.java:33`).
- Faltam testes para 405 e 415, barra final, `HEAD`/`OPTIONS`, id negativo e username em outra caixa.
- Dependências não foram verificadas contra CVEs.
- A listagem não tem paginação.

## CI

`.github/workflows/ci.yml` roda `mvn -B clean verify` com Java 21 (Temurin) em pull requests e em pushes para `main` e `lab-seguro`. As permissões do workflow são só leitura (`contents: read`).

## Estrutura

```text
src/main/java/br/com/awsgugtm/tasks/
  config/SecurityConfig.java        HTTP Basic, usuários em memória
  domain/Task.java                  entidade (id, title, owner)
  dto/TaskResponse.java             saída (id, title)
  dto/ErrorResponse.java            corpo de erro fixo
  repository/TaskRepository.java    findByOwner, findByIdAndOwner
  service/TaskService.java          regras de acesso e log
  web/TaskController.java           GET /api/tasks e /api/tasks/{id}
  web/TaskIdParser.java             validação estrita do id
  web/ApiExceptionHandler.java      respostas de erro genéricas
  seed/DataSeeder.java              tarefas fictícias 42 e 43
.kiro/steering/security.md          regras do projeto
.kiro/specs/secure-task-access/     requisitos, design e tasks
```

## Aviso

Projeto de demonstração. Dados e credenciais são fictícios. Não exponha esta aplicação em rede.