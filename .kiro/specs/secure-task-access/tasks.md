# Implementation Plan: secure-task-access

## Overview

Este plano corrige a falha BOLA/IDOR em `GET /api/tasks/{id}` seguindo a ordem pedida: primeiro o teste de regressão, depois a demonstração da falha na versão atual, depois a correção mínima conforme o design e, por fim, a repetição dos testes com revisão do diff. Os testes adicionais do Requisito 12, os testes de log e as propriedades (PBT) vêm depois.

A linguagem de implementação é Java (Spring Boot, Spring Security, JUnit 5, MockMvc, jqwik), a mesma do design. O pacote base é `br.com.awsgugtm.tasks`.

Regras que valem para todas as tarefas:

- Alterações em segurança só são aplicadas após aprovação humana explícita (Req. 13.1, 13.2). Os checkpoints 3 e 7 existem para isso.
- Todo comando de terminal é apresentado por completo e só roda após aprovação (Req. 13.4, 13.5). Sem deploy e sem acesso a serviços externos (Req. 13.3, 13.6).
- Baixar dependências do Maven acessa um repositório externo. Exige autorização explícita do revisor, ou uso do repositório local (`mvn -o`) se os artefatos já existirem.
- Não alterar testes existentes para mascarar defeitos. A única mudança permitida em teste existente é a inversão do teste de caracterização, exigida pelo Req. 12.10.
- Não desabilitar autenticação, autorização nem CSRF, e não usar `@Disabled` nem `@WithMockUser` para contornar autenticação (Req. 12.11, 12.12).
- Preservar `SecurityConfig`, `application.properties` e `DataSeeder` sem alterações (Req. 11.1 a 11.3).
- Usar apenas dados fictícios. Nenhum segredo, dado real ou PII em código, testes ou logs (Req. 10.4).

## Tasks

- [x] 1. Escrever o teste de regressão que falha na versão atual
  - [x] 1.1 Substituir o teste de caracterização por um teste que espera 404
    - Em `src/test/java/br/com/awsgugtm/tasks/web/TaskControllerTest.java`, substituir `caracterizacao_BOLA_bobAcessaTarefa42DeAlice_retorna200` por `bobAcessaTarefa42DeAlice_retorna404`.
    - Usar `httpBasic("bob", "bob-demo")` contra a configuração real e esperar status 404 em `GET /api/tasks/42`.
    - Verificar que o corpo não contém `id`, `title` nem `owner` da tarefa 42, nem os valores desses campos.
    - Não manter nenhum teste que espere 200 para Bob lendo a tarefa 42.
    - Não alterar os demais testes (`aliceAcessaPropriaTarefa42_retorna200`, `anonimo_retorna401`, `tarefaInexistente_retorna404`, `listagemRetornaApenasTarefasDoUsuarioAutenticado`).
    - _Requirements: 12.3, 12.10, 12.11, 4.1, 4.3_

- [x] 2. Checkpoint - Demonstrar a falha na versão atual
  - Apresentar ao revisor o comando completo, por exemplo `mvn -o test` na raiz do projeto, e aguardar aprovação explícita antes de rodar. Sem deploy e sem serviços externos.
  - Se o modo offline falhar por falta de artefatos locais, pedir autorização explícita antes de qualquer comando que baixe dependências.
  - Rodar os testes e confirmar que `bobAcessaTarefa42DeAlice_retorna404` falha porque a API ainda retorna 200 (defeito BOLA reproduzido). Registrar a saída da falha. Os demais testes existentes devem continuar passando.
  - Se o teste passar, ou se falhar por outro motivo, parar e perguntar ao usuário.

- [x] 3. Checkpoint - Aprovação humana antes das alterações de segurança
  - Apresentar ao revisor o resumo das alterações previstas na tarefa 4 (`TaskRepository`, `TaskService`, `TaskController`, `TaskIdParser`, `ErrorResponse`, exceções de domínio, `ApiExceptionHandler`) e obter aprovação explícita antes de aplicá-las (Req. 13.1, 13.2).
  - Confirmar na revisão os pontos abertos do design: conflito entre Req. 2.6 e 8.5 (username nulo ou vazio lança `IllegalArgumentException`; `id` nulo retorna `Optional.empty()`), e `id` negativo ou zero resultando em 404, não 400.
  - Sem aprovação, não modificar nenhum arquivo e informar que a aprovação está pendente.

- [x] 4. Implementar a correção mínima
  - [x] 4.1 Adicionar a consulta atômica no repositório e ajustar o service
    - Em `TaskRepository`, adicionar `Optional<Task> findByIdAndOwner(Long id, String owner)`.
    - Em `TaskService`, criar `Optional<Task> findByIdAndOwner(Long id, String username)` e remover `findByIdVulnerable` junto com os comentários que o descrevem.
    - Validar o username (nulo, vazio ou só espaços) em `listByOwner` e em `findByIdAndOwner`, lançando `IllegalArgumentException` com mensagem genérica, sem consultar o repositório.
    - Retornar `Optional.empty()` sem consultar o repositório quando o `id` for nulo.
    - Quando o resultado for vazio, registrar uma única entrada de log `INFO` com o texto fixo `tarefa nao encontrada` e o `id`, sem username, owner real nem título. O log é o mesmo para id inexistente e tarefa de outro dono.
    - Nenhum chamador do fluxo de leitura por id pode usar `repository.findById`.
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 2.4, 2.6, 6.4, 10.2_

  - [x] 4.2 Criar `TaskIdParser`, `ErrorResponse` e as exceções de domínio
    - Criar `TaskIdParser` (pacote `web`, método estático `parse(String)`) que aceita somente `-?[0-9]+` dentro do intervalo de `Long` e lança `InvalidTaskIdException` nos demais casos. Rejeitar vazio, espaços, `+`, decimal, hexadecimal, octal com prefixo, texto e overflow.
    - Criar o record `ErrorResponse(String code, String message)` no pacote `dto`.
    - Criar `TaskNotFoundException` e `InvalidTaskIdException` sem mensagem dinâmica e sem o valor recebido.
    - _Requirements: 9.3, 9.4, 6.3, 9.5_

  - [x] 4.3 Criar `ApiExceptionHandler`
    - Criar `@RestControllerAdvice` no pacote `web` com a tabela do design: `TaskNotFoundException` retorna 404 com `ErrorResponse("NOT_FOUND", "Tarefa não encontrada")`; `InvalidTaskIdException` e `IllegalArgumentException` retornam 400 com `ErrorResponse("BAD_REQUEST", "Requisição inválida")`; `AuthenticationCredentialsNotFoundException` retorna 401 sem corpo; `Exception` retorna 500 com `ErrorResponse("INTERNAL_ERROR", "Erro interno")`.
    - Montar todos os corpos de erro só com constantes: sem `id`, `path`, `timestamp`, username, título ou owner.
    - No handler genérico, registrar somente o nome do tipo da exceção, sem mensagem nem stack trace.
    - _Requirements: 4.2, 4.3, 6.1, 6.2, 6.3, 6.4, 9.4, 9.5, 10.1, 2.5_

  - [x] 4.4 Ajustar o `TaskController`
    - Em `getById`, receber `@PathVariable("id") String rawId` e `Principal principal`, converter o id com `TaskIdParser.parse` e chamar `service.findByIdAndOwner(id, principal.getName())` com exatamente esses dois valores.
    - Se o resultado for vazio, lançar `TaskNotFoundException`. Se houver resultado, retornar `TaskResponse(id, title)`.
    - Em `list` e `getById`, lançar `AuthenticationCredentialsNotFoundException` quando `principal` for nulo, antes de chamar o service.
    - Não usar `@RequestParam`, `@RequestHeader` nem `@RequestBody`. Os tipos de retorno são `TaskResponse` e `List<TaskResponse>`, nunca `Task`.
    - Não alterar `SecurityConfig`, `application.properties` nem `DataSeeder`.
    - _Requirements: 1.4, 1.7, 2.1, 2.2, 2.3, 2.5, 3.1, 3.2, 3.3, 3.4, 9.1, 9.2_

- [x] 5. Checkpoint - Repetir os testes e revisar o diff
  - Apresentar ao revisor o comando completo (por exemplo `mvn -o test`) e aguardar aprovação antes de rodar.
  - Confirmar que `bobAcessaTarefa42DeAlice_retorna404` agora passa e que todos os testes existentes continuam passando sem alteração.
  - Mostrar o diff (`git diff`) ao revisor. Verificar que `SecurityConfig`, `application.properties`, `DataSeeder` e os testes existentes (exceto a inversão do teste de caracterização feita na tarefa 1.1) não foram alterados e que nenhum controle foi desabilitado.
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Completar os testes de autorização e de log
  - [x] 6.1 Adicionar os testes MockMvc do Requisito 12
    - Em `TaskControllerTest`, apenas acrescentar testes novos, sem alterar os existentes: Alice em `/api/tasks/42` (200, `id` 42, sem `owner`); Bob em `/api/tasks/43` (200, `id` 43, sem `owner`); Alice em `/api/tasks/43` (404 sem dados da tarefa 43).
    - Bob consulta 42 e depois 999999: mesmo status e mesmo corpo, ignorando cabeçalhos de data.
    - Sem credenciais, 42 e 999999 retornam 401 sem dados de tarefa. Alice em 999999 retorna 404 sem dados.
    - Listagem: Alice recebe exatamente a tarefa 42 e Bob exatamente a 43.
    - Owner enviado pelo cliente: Bob com `alice` (query e cabeçalho) em 42 recebe 404; Alice com `bob` (query e cabeçalho) em 42 recebe 200 com `id` 42.
    - `id` inválido (`abc`, `1.5`, `0x1F`, `9223372036854775808`) retorna 400 com corpo genérico sem eco do valor.
    - Usar `httpBasic("alice", "alice-demo")` e `httpBasic("bob", "bob-demo")` contra a configuração real.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8, 12.9, 12.11, 12.12, 5.1, 5.2, 6.3_

  - [x] 6.2 Escrever testes unitários do `TaskService`
    - Criar `TaskServiceTest` cobrindo: (a) o dono consulta a própria tarefa e recebe o resultado; (b) outro usuário recebe `Optional.empty()`; (c) id inexistente produz o mesmo resultado que (b).
    - Verificar com Mockito que o service chama somente `findByIdAndOwner` e nunca `findById`.
    - Cobrir username nulo, vazio e só espaços (`IllegalArgumentException` sem interação com o repositório) e `id` nulo (`Optional.empty()` sem consultar o repositório).
    - _Requirements: 8.6, 8.4, 8.5, 2.4, 2.6_

  - [x] 6.3 Escrever testes do `TaskRepository.findByIdAndOwner`
    - Criar teste `@DataJpaTest` com owner correto, owner errado, owner em outra caixa (`ALICE` não encontra tarefa de `alice`) e id inexistente.
    - Usar tarefas fictícias com ids fora de 42 e 43.
    - _Requirements: 8.1, 1.2_

  - [x] 6.4 Escrever testes do controller com service mockado
    - Criar teste que verifica que o service recebe exatamente `(id, username do Principal)` e que `Principal` nulo não chama o service e resulta em 401.
    - Verificar que os métodos do controller retornam `TaskResponse` ou `List<TaskResponse>`.
    - Sem desabilitar filtros de segurança nos testes de autorização.
    - _Requirements: 2.3, 2.5, 9.2_

  - [x] 6.5 Escrever testes negativos de log
    - Criar teste com appender de log capturado cobrindo: (a) nenhuma entrada contém senha, valor de `Authorization` ou PII; (b) tarefa não encontrada gera uma única entrada com `id` e indicador fixo, sem título nem owner real, igual para id inexistente e tarefa de outro dono; (c) `Authorization` inválido ou ausente não ecoa o valor nem o usuário informado.
    - _Requirements: 10.1, 10.2, 10.3, 10.5_

  - [x] 6.6 Escrever testes de fumaça da configuração local
    - Verificar que `alice` e `bob` autenticam e que um terceiro usuário e senha incorreta recebem 401 sem dados de tarefa.
    - Verificar que o seeder cria exatamente duas tarefas: 42 (`alice`) e 43 (`bob`).
    - Verificar por reflexão que `TaskService` não declara `findByIdVulnerable`.
    - _Requirements: 11.1, 11.3, 11.4, 11.5, 8.3_

- [x] 7. Checkpoint - Aprovação para alterar o `pom.xml` e baixar dependências
  - Apresentar ao revisor a alteração prevista no `pom.xml` (`net.jqwik:jqwik` e `net.jqwik:jqwik-spring`, escopo `test`) e obter aprovação explícita antes de aplicá-la (Req. 13.1, 13.2).
  - Informar que baixar essas dependências acessa um repositório externo. Só rodar o comando de build, apresentado por completo, após autorização explícita do revisor. Se não houver autorização, manter o `pom.xml` inalterado e pular as tarefas de propriedade (8.x e 9.x), que são opcionais.
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Adicionar o jqwik ao projeto
  - [~] 8.1 Adicionar as dependências de teste no `pom.xml`
    - Adicionar `net.jqwik:jqwik` e `net.jqwik:jqwik-spring` com escopo `test`, sem alterar as demais dependências nem adicionar repositórios ou endereços externos.
    - _Requirements: 13.1, 11.2_

- [ ] 9. Escrever os testes de propriedade (PBT)
  - Cada propriedade é um teste separado, com no mínimo 100 iterações (`@Property(tries = 100)`) e com o comentário `Feature: secure-task-access, Property {n}: {texto}`. Dados fictícios. Os testes que gravam tarefas devem limpar os dados e usar ids fora de 42 e 43.

  - [ ]* 9.1 Escrever teste de propriedade para acesso por id somente do dono
    - **Property 1: Acesso por id somente para o proprietário exato**
    - **Validates: Requirements 1.2, 3.1, 8.1, 8.4**

  - [ ]* 9.2 Escrever teste de propriedade para respostas indistinguíveis
    - **Property 2: Respostas de não proprietário e de id inexistente são indistinguíveis**
    - **Validates: Requirements 1.5, 3.5, 4.1, 4.2, 4.3, 6.1, 6.2, 9.5**

  - [ ]* 9.3 Escrever teste de propriedade para requisições sem autenticação válida
    - **Property 3: Requisição sem autenticação válida nunca devolve dados**
    - **Validates: Requirements 1.6, 4.4, 5.1, 5.3, 5.4, 11.4, 11.5**

  - [ ]* 9.4 Escrever teste de propriedade para owner enviado pelo cliente
    - **Property 4: Owner enviado pelo cliente é ignorado**
    - **Validates: Requirements 1.7, 2.2, 3.4**

  - [ ]* 9.5 Escrever teste de propriedade para o formato da resposta
    - **Property 5: Resposta de sucesso expõe somente id e title**
    - **Validates: Requirements 1.4, 3.2, 3.3, 7.3, 9.1**

  - [ ]* 9.6 Escrever teste de propriedade para a listagem
    - **Property 6: Listagem contém exatamente as tarefas do usuário, sem duplicatas**
    - **Validates: Requirements 1.3, 7.1, 7.2, 7.5**

  - [ ]* 9.7 Escrever teste de propriedade para o parser de id
    - **Property 7: O parser de id aceita somente inteiros decimais dentro do intervalo de Long**
    - **Validates: Requirements 9.3, 9.4**

  - [ ]* 9.8 Escrever teste de propriedade para id inválido
    - **Property 8: Id inválido gera 400 genérico sem eco**
    - **Validates: Requirements 6.3, 9.4, 9.5**

  - [ ]* 9.9 Escrever teste de propriedade para username inválido no service
    - **Property 9: Username inválido é rejeitado antes de consultar o repositório**
    - **Validates: Requirements 2.4, 2.6**

- [x] 10. Checkpoint final - Revisão e testes
  - Apresentar o comando completo de teste ao revisor e aguardar aprovação antes de rodar. Sem deploy e sem serviços externos.
  - Confirmar que a suíte inteira passa, sem testes ignorados ou desabilitados.
  - Mostrar o diff final para revisão humana, com atenção ao `pom.xml` e aos arquivos de segurança.
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tarefas marcadas com `*` são opcionais (propriedades) e podem ser puladas para um MVP. Os testes do Requisito 12, de service, de repositório, de log e de fumaça não são opcionais porque os requisitos os exigem.
- Cada tarefa referencia requisitos específicos para rastreabilidade, e cada propriedade referencia o número da propriedade do design.
- As tarefas 2, 5 e 10 só rodam comandos após aprovação explícita. As tarefas 3 e 7 são portões de aprovação humana para alterações de segurança e do `pom.xml`.
- A tarefa 4.4 depende de 4.1, 4.2 e 4.3, porque a remoção de `findByIdVulnerable` quebra a compilação do controller até que ele seja ajustado.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["4.1", "4.2"] },
    { "id": 2, "tasks": ["4.3"] },
    { "id": 3, "tasks": ["4.4"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3", "6.4", "6.5", "6.6", "8.1"] },
    { "id": 5, "tasks": ["9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7", "9.8", "9.9"] }
  ]
}
```
