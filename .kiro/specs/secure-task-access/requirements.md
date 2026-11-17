# Requirements Document

## Introduction

Este documento especifica a correção da falha de autorização por objeto (BOLA/IDOR, OWASP API1:2023) na API Secure Tasks (Spring Boot, Spring Security, pacote `br.com.awsgugtm.tasks`).

**Situação atual (baseada no código existente):**

- `TaskController.getById` (`GET /api/tasks/{id}`) não recebe o `Principal`. O método chama `TaskService.findByIdVulnerable(id)`, que usa `repository.findById(id)` sem considerar o proprietário. Qualquer usuário autenticado lê a tarefa de outro usuário (ex.: `bob` lê a tarefa 42 de `alice` e recebe 200).
- `TaskController.list` (`GET /api/tasks`) já usa `principal.getName()` e `TaskService.listByOwner`, e já filtra pelo proprietário.
- `TaskRepository` declara apenas `findByOwner`. Não há consulta combinada por id e proprietário.
- `SecurityConfig` exige autenticação em todas as requisições (HTTP Basic, usuários em memória `alice` e `bob`). Esta configuração é exclusiva do laboratório local.
- `TaskResponse` (record com `id` e `title`) já é o DTO de saída e não expõe o campo `owner`.
- `TaskControllerTest` contém o teste de caracterização `caracterizacao_BOLA_bobAcessaTarefa42DeAlice_retorna200`, que documenta o defeito atual e deve ser corrigido, não removido nem enfraquecido.
- `DataSeeder` cria a tarefa 42 (alice) e a tarefa 43 (bob), com dados fictícios.

**Análise de identidade e limites de confiança:**

| Elemento | Origem | Confiança |
|---|---|---|
| Identidade (username) | `SecurityContext` do Spring Security, após autenticação HTTP Basic | Confiável |
| `id` da tarefa (path) | Cliente | Não confiável |
| Cabeçalhos, parâmetros e corpo da requisição | Cliente | Não confiável |
| Proprietário da tarefa | Banco de dados (campo `owner` da entidade `Task`) | Confiável |

O proprietário usado em qualquer consulta deve vir somente da identidade autenticada. Nenhum valor de proprietário enviado pelo cliente pode ser usado.

**Fora de escopo:** criação, alteração e exclusão de tarefas; substituição do mecanismo de autenticação (HTTP Basic e usuários em memória permanecem como configuração local do laboratório); deploy; acesso a serviços externos.

## Glossary

- **API**: Aplicação Spring Boot Secure Tasks, pacote `br.com.awsgugtm.tasks`.
- **Task_Controller**: Componente web `TaskController` que expõe os endpoints `/api/tasks`.
- **Task_Service**: Componente `TaskService` que aplica as regras de acesso às tarefas.
- **Task_Repository**: Componente `TaskRepository` que acessa a persistência das tarefas.
- **Tarefa**: Registro `Task` com `id`, `title` e `owner`.
- **Proprietário**: Username armazenado no campo `owner` da Tarefa.
- **Usuário_Autenticado**: Identidade (username) obtida do `SecurityContext` do Spring Security, após autenticação bem-sucedida.
- **Requisição_Não_Autenticada**: Requisição sem credenciais válidas.
- **Task_Response**: DTO de saída `TaskResponse`, que contém apenas `id` e `title`.
- **BOLA**: Broken Object Level Authorization (OWASP API1:2023), acesso a objeto de outro usuário por manipulação do identificador.
- **PII**: Informação pessoal identificável.
- **Logs_da_API**: Registros de log emitidos pela API.

## Requirements

### Requirement 1: Privacidade das tarefas

**User Story:** Como usuário, quero que minhas tarefas sejam privadas, para que nenhum outro usuário possa lê-las.

#### Acceptance Criteria

1. THE API SHALL tratar toda Tarefa como privada ao seu Proprietário, de modo que o Proprietário seja definido exclusivamente pelo servidor e nunca por valor de owner recebido do cliente.
2. WHEN um Usuário_Autenticado solicita a leitura de uma Tarefa, THE API SHALL conceder acesso somente se o username do Usuário_Autenticado for idêntico (comparação exata, sensível a maiúsculas e minúsculas) ao Proprietário dessa Tarefa.
3. WHEN um Usuário_Autenticado solicita `GET /api/tasks`, THE API SHALL retornar somente as Tarefas cujo Proprietário seja o Usuário_Autenticado, ou uma lista vazia se ele não possuir Tarefas.
4. WHEN um Usuário_Autenticado solicita `GET /api/tasks/{id}` de uma Tarefa da qual é Proprietário, THE API SHALL retornar a Tarefa por meio de Task_Response, sem expor a entidade diretamente.
5. IF um Usuário_Autenticado solicita `GET /api/tasks/{id}` de uma Tarefa cujo Proprietário é outro usuário, ou de um `id` inexistente, THEN THE API SHALL negar a leitura com uma resposta de erro idêntica nos dois casos, sem revelar a existência da Tarefa nem dados dela.
6. IF uma requisição a `GET /api/tasks` ou `GET /api/tasks/{id}` for recebida sem identidade autenticada válida, THEN THE API SHALL rejeitar a requisição com erro de autenticação e não retornar nenhum dado de Tarefa.
7. THE API SHALL aplicar a regra de privacidade a todos os endpoints que retornam Tarefas, incluindo `GET /api/tasks` e `GET /api/tasks/{id}`, e SHALL ignorar qualquer parâmetro, cabeçalho ou campo de corpo enviado pelo cliente que tente indicar outro Proprietário.

### Requirement 2: Origem da identidade

**User Story:** Como responsável pela segurança, quero que a identidade venha somente do Spring Security, para que o cliente não consiga se passar por outro usuário.

#### Acceptance Criteria

1. WHEN o Task_Controller processa qualquer requisição para `/api/tasks`, THE Task_Controller SHALL obter o username do Usuário_Autenticado exclusivamente a partir do contexto de autenticação do Spring Security.
2. WHEN uma requisição contém um valor de proprietário, usuário ou identidade em parâmetros de consulta, cabeçalhos (exceto o cabeçalho de credenciais processado pelo Spring Security), caminho ou corpo, THE API SHALL ignorar esse valor e processar a requisição com o username do Usuário_Autenticado, de modo que o resultado seja idêntico ao de uma requisição sem esse valor.
3. WHEN o Task_Controller recebe uma requisição para `GET /api/tasks/{id}` de um Usuário_Autenticado, THE Task_Controller SHALL repassar ao Task_Service exatamente dois valores de identificação: o `id` do caminho e o username do Usuário_Autenticado.
4. THE Task_Service SHALL receber o username do Usuário_Autenticado como parâmetro explícito, não nulo e não vazio, em toda operação de consulta de Tarefas.
5. IF o Task_Controller recebe uma requisição para `/api/tasks` sem Usuário_Autenticado no contexto do Spring Security, THEN THE API SHALL rejeitar a requisição com indicação de falta de autenticação, sem invocar o Task_Service e sem retornar dados de Tarefas.
6. IF uma operação de consulta do Task_Service recebe um username nulo ou vazio, THEN THE Task_Service SHALL rejeitar a operação com erro de argumento inválido, sem consultar o Task_Repository e sem retornar dados de Tarefas.

### Requirement 3: Consulta por id pelo proprietário

**User Story:** Como proprietário de uma tarefa, quero consultá-la pelo id, para que eu veja seus dados.

#### Acceptance Criteria

1. WHEN o Usuário_Autenticado, que é o Proprietário da Tarefa, envia `GET /api/tasks/{id}` com um `id` existente, THE API SHALL responder com status HTTP 200.
2. WHEN a API responde com status HTTP 200 para `GET /api/tasks/{id}`, THE API SHALL retornar um Task_Response contendo exatamente os campos `id` e `title` da Tarefa, com os valores persistidos, sem nenhum outro campo.
3. THE API SHALL omitir o campo `owner` do corpo da resposta de `GET /api/tasks/{id}`.
4. WHEN a API processa `GET /api/tasks/{id}`, THE API SHALL determinar o Proprietário exclusivamente a partir da identidade do Usuário_Autenticado, ignorando qualquer valor de owner enviado pelo cliente em parâmetros, cabeçalhos ou corpo.
5. IF o Usuário_Autenticado envia `GET /api/tasks/{id}` para uma Tarefa da qual não é o Proprietário, ou para um `id` inexistente, THEN THE API SHALL responder com o mesmo status HTTP 404 e o mesmo corpo de erro nos dois casos, sem revelar a existência da Tarefa nem dados dela, e sem alterar nenhuma Tarefa.
6. IF a requisição `GET /api/tasks/{id}` não contém credenciais válidas, ou contém um `id` com formato inválido, THEN THE API SHALL rejeitar a requisição com status HTTP 401 (sem credenciais válidas) ou 400 (`id` inválido), sem retornar dados de nenhuma Tarefa.

### Requirement 4: Acesso de não proprietário

**User Story:** Como proprietário de uma tarefa, quero que a existência dela não seja revelada a outros usuários, para que minha privacidade seja preservada.

#### Acceptance Criteria

1. IF o Usuário_Autenticado não é o Proprietário da Tarefa solicitada, THEN THE API SHALL responder `GET /api/tasks/{id}` com status HTTP 404 e SHALL NOT alterar nenhum dado da Tarefa.
2. IF o Usuário_Autenticado não é o Proprietário da Tarefa solicitada, THEN THE API SHALL produzir uma resposta idêntica, em status, no conjunto de cabeçalhos de resposta (nomes e valores, exceto cabeçalhos de data/hora e de identificação de requisição) e em corpo byte a byte, à resposta produzida para um `id` inexistente.
3. IF o Usuário_Autenticado não é o Proprietário da Tarefa solicitada, THEN THE API SHALL omitir `id`, `title`, `owner` e qualquer outro campo da Tarefa do corpo da resposta, de modo que o corpo não contenha nenhum valor derivado da Tarefa.
4. IF a requisição `GET /api/tasks/{id}` não contém credenciais válidas, THEN THE API SHALL rejeitar a requisição como não autenticada, sem consultar nem revelar a existência da Tarefa.

### Requirement 5: Requisição não autenticada

**User Story:** Como responsável pela segurança, quero que requisições sem autenticação sejam rejeitadas, para que somente usuários identificados acessem a API.

#### Acceptance Criteria

1. IF uma Requisição_Não_Autenticada é enviada para `GET /api/tasks/{id}`, THEN THE API SHALL responder com status HTTP 401, sem executar a consulta da Tarefa e sem alterar nenhum dado armazenado.
2. IF uma Requisição_Não_Autenticada é enviada para `GET /api/tasks`, THEN THE API SHALL responder com status HTTP 401, sem executar a listagem de Tarefas e sem alterar nenhum dado armazenado.
3. IF uma Requisição_Não_Autenticada é enviada, THEN THE API SHALL retornar um corpo de resposta que não contenha nenhum campo de Tarefa (`id`, `title` ou `owner`) nem dados de outros usuários.
4. IF uma requisição contém credenciais ausentes, malformadas ou inválidas, THEN THE API SHALL tratá-la como Requisição_Não_Autenticada e responder com status HTTP 401.

### Requirement 6: Identificador inexistente

**User Story:** Como usuário autenticado, quero receber um erro claro ao consultar um id que não existe, para que eu saiba que a tarefa não foi encontrada.

#### Acceptance Criteria

1. IF o Usuário_Autenticado envia `GET /api/tasks/{id}` com um `id` de formato válido que não corresponde a nenhuma Tarefa, THEN THE API SHALL responder com status HTTP 404, sem dados de Tarefa no corpo e sem alterar nenhuma Tarefa armazenada.
2. IF o Usuário_Autenticado envia `GET /api/tasks/{id}` com um `id` que não corresponde a nenhuma Tarefa, THEN THE API SHALL responder com o mesmo status HTTP (404), a mesma estrutura de corpo e a mesma mensagem de erro usadas no caso de Tarefa pertencente a outro usuário, de modo que as duas respostas sejam idênticas byte a byte, exceto por campos de data/hora e identificador de requisição, se existirem.
3. IF o Usuário_Autenticado envia `GET /api/tasks/{id}` com um `id` de formato inválido, THEN THE API SHALL rejeitar a requisição com status HTTP 400, sem consultar Tarefas e sem incluir o valor de `id` recebido, dados de Tarefas ou detalhes internos (stack trace, nomes de classes) no corpo da resposta.
4. IF o Usuário_Autenticado envia `GET /api/tasks/{id}` com um `id` inexistente, THEN THE API SHALL registrar no log apenas o evento de "tarefa não encontrada", sem segredos, dados reais, PII do usuário ou token de autenticação.

### Requirement 7: Listagem restrita ao proprietário

**User Story:** Como usuário autenticado, quero listar somente as minhas tarefas, para que eu não veja tarefas de terceiros.

#### Acceptance Criteria

1. WHEN o Usuário_Autenticado envia `GET /api/tasks`, THE API SHALL responder com status HTTP 200 e com uma lista que contém somente Tarefas cujo Proprietário é o Usuário_Autenticado, determinando o Proprietário exclusivamente a partir da identidade autenticada e ignorando qualquer identificador de proprietário enviado pelo cliente.
2. WHEN o Usuário_Autenticado não possui Tarefas e envia `GET /api/tasks`, THE API SHALL responder com status HTTP 200 e com uma lista vazia, mesmo que existam Tarefas de outros Proprietários.
3. THE API SHALL retornar cada item da listagem como Task_Response, sem expor a entidade `Task` diretamente.
4. IF a requisição `GET /api/tasks` é enviada sem identidade autenticada válida, THEN THE API SHALL responder com status HTTP 401, não retornar nenhuma Tarefa e não revelar a existência de Tarefas de qualquer Proprietário.
5. WHEN o Usuário_Autenticado possui Tarefas e envia `GET /api/tasks`, THE API SHALL retornar todas as Tarefas do Usuário_Autenticado, sem duplicatas.

### Requirement 8: Consulta atômica por id e proprietário

**User Story:** Como mantenedor, quero que a verificação de propriedade ocorra na própria consulta, para que não exista janela entre buscar e autorizar.

#### Acceptance Criteria

1. THE Task_Repository SHALL expor uma operação única de consulta que recebe o `id` da Tarefa e o username do Proprietário e retorna a Tarefa somente quando ambos coincidem com os de um mesmo registro, em uma única execução de consulta, sem etapa separada de verificação de propriedade.
2. WHEN o Task_Service consulta uma Tarefa por `id`, THE Task_Service SHALL usar a operação única de consulta por `id` e Proprietário do Task_Repository, informando como Proprietário o username da identidade autenticada e nunca um valor recebido do cliente.
3. THE Task_Service SHALL NOT conter o método `findByIdVulnerable` nem qualquer chamada à consulta somente por `id` no fluxo de leitura de Tarefas por identificador.
4. IF a operação única de consulta não retorna Tarefa, THEN THE Task_Service SHALL sinalizar ausência de resultado com o mesmo resultado observável pelo chamador, sem distinguir entre `id` inexistente e Tarefa de outro Proprietário, e sem incluir dados da Tarefa, do Proprietário real ou do username consultado na resposta.
5. IF o `id` recebido é nulo ou o username da identidade autenticada é nulo ou vazio, THEN THE Task_Service SHALL sinalizar ausência de resultado sem executar a consulta no Task_Repository.
6. THE Task_Service SHALL ter testes automatizados que verifiquem: (a) o Proprietário consulta a própria Tarefa e recebe o resultado; (b) um usuário autenticado diferente consulta a Tarefa de outro Proprietário e recebe ausência de resultado; (c) um `id` inexistente produz o mesmo resultado observável que o caso (b).

### Requirement 9: Uso de DTO

**User Story:** Como responsável pela segurança, quero que a API retorne apenas DTOs, para que atributos internos da entidade não sejam expostos.

#### Acceptance Criteria

1. WHEN um endpoint do Task_Controller retorna uma ou mais Tarefas ao cliente, THE Task_Controller SHALL serializar cada Tarefa somente como Task_Response, sem incluir nenhum atributo da entidade `Task` que não esteja declarado em Task_Response.
2. THE Task_Controller SHALL declarar Task_Response, ou uma coleção de Task_Response, e não a entidade `Task`, como tipo de retorno em todas as assinaturas de endpoint que retornam Tarefas.
3. WHEN um endpoint do Task_Controller recebe o parâmetro `id` no caminho, THE Task_Controller SHALL aceitar o valor somente se ele for um número inteiro decimal dentro do intervalo de `Long`.
4. IF o parâmetro `id` do caminho estiver fora do formato ou do intervalo descrito no critério 3 (por exemplo, texto não numérico, decimal, vazio ou acima de 9223372036854775807), THEN THE API SHALL responder com status HTTP 400, com corpo contendo uma mensagem de erro genérica, sem expor nomes de classes, stack trace, mensagens de exceção internas ou detalhes de persistência, e sem consultar nem alterar dados de Tarefas.
5. IF um endpoint do Task_Controller retorna uma resposta de erro (status 4xx ou 5xx), THEN THE API SHALL montar o corpo da resposta sem incluir atributos da entidade `Task`.

### Requirement 10: Logs e dados sensíveis

**User Story:** Como responsável pela privacidade, quero que os logs não contenham dados sensíveis, para que não haja vazamento por meio de registros.

#### Acceptance Criteria

1. WHEN a API grava uma entrada nos Logs_da_API, THE API SHALL omitir dessa entrada senhas, o valor do cabeçalho `Authorization`, segredos (tokens, chaves e credenciais) e PII.
2. IF uma requisição resulta em Tarefa não encontrada (inclusive Tarefa de outro Proprietário), THEN THE API SHALL registrar uma única entrada de log contendo somente o `id` da Tarefa solicitada e um indicador de resultado único e idêntico para os dois casos, sem o título da Tarefa e sem o Proprietário real da Tarefa.
3. IF uma requisição contém um cabeçalho `Authorization` inválido ou ausente, THEN THE API SHALL registrar somente um indicador do resultado de autenticação, sem o valor do cabeçalho e sem o identificador do usuário informado pelo cliente.
4. THE API SHALL usar somente credenciais e dados fictícios em código, configuração, testes, logs e documentação.
5. WHEN os testes automatizados de log são executados, THE suíte de testes SHALL incluir pelo menos um teste negativo para cada caso dos critérios 1, 2 e 3, verificando a ausência de dados sensíveis na saída de log capturada.

### Requirement 11: Configuração local preservada

**User Story:** Como instrutor do laboratório, quero manter a configuração local atual, para que o ambiente de demonstração continue funcionando.

#### Acceptance Criteria

1. THE API SHALL manter HTTP Basic como único mecanismo de autenticação e exatamente dois usuários em memória, `alice` e `bob`, definidos em `SecurityConfig` como configuração local do laboratório, sem adicionar, remover ou renomear usuários.
2. THE API SHALL manter `application.properties` sem novos segredos, credenciais nem endereços (hosts, URLs ou portas) de serviços externos.
3. WHEN a aplicação for iniciada, THE API SHALL executar o `DataSeeder` para criar exatamente duas Tarefas fictícias: a Tarefa 42 pertencente a `alice` e a Tarefa 43 pertencente a `bob`, sem dados reais nem PII.
4. IF uma requisição a qualquer recurso da API não contiver credenciais válidas, THEN THE API SHALL rejeitar a requisição como não autenticada, sem retornar dados de Tarefas e sem alterar o estado armazenado, conforme `SecurityConfig`.
5. IF uma requisição contiver credenciais de usuário diferente de `alice` ou `bob`, ou senha incorreta para esses usuários, THEN THE API SHALL rejeitar a requisição como não autenticada, sem retornar dados de Tarefas.

### Requirement 12: Testes de autorização

**User Story:** Como mantenedor, quero testes positivos e negativos de autorização, para que regressões de BOLA sejam detectadas.

#### Acceptance Criteria

1. THE suíte de testes SHALL incluir um teste positivo em que `alice` autenticada consulta `GET /api/tasks/42` e recebe status HTTP 200 com o campo `id` igual a 42 e sem o campo `owner` no corpo da resposta.
2. THE suíte de testes SHALL incluir um teste positivo em que `bob` autenticado consulta `GET /api/tasks/43` e recebe status HTTP 200 com o campo `id` igual a 43 e sem o campo `owner` no corpo da resposta.
3. THE suíte de testes SHALL incluir um teste negativo em que `bob` autenticado consulta `GET /api/tasks/42` e recebe status HTTP 404 com corpo que não contém nenhum dos campos da Tarefa 42 (`id`, `title`, `owner`) nem valores desses campos.
4. THE suíte de testes SHALL incluir um teste negativo em que `alice` autenticada consulta `GET /api/tasks/43` e recebe status HTTP 404 com corpo que não contém nenhum dos campos da Tarefa 43 (`id`, `title`, `owner`) nem valores desses campos.
5. THE suíte de testes SHALL incluir um teste em que `bob` autenticado consulta `GET /api/tasks/42` e, em seguida, `GET /api/tasks/{id}` com um `id` inexistente (por exemplo, 999999), e verifica que as duas respostas têm o mesmo status HTTP e o mesmo corpo, desconsiderando apenas cabeçalhos de data e horário.
6. THE suíte de testes SHALL incluir um teste em que uma Requisição_Não_Autenticada para `GET /api/tasks/42` e outra para `GET /api/tasks/{id}` com um `id` inexistente recebem status HTTP 401, sem dados de Tarefa no corpo.
7. THE suíte de testes SHALL incluir um teste em que `alice` autenticada consulta `GET /api/tasks/{id}` com um `id` inexistente (por exemplo, 999999) e recebe status HTTP 404 sem dados de Tarefa no corpo.
8. THE suíte de testes SHALL incluir um teste em que a listagem de `alice` autenticada contém exatamente 1 Tarefa, com `id` 42, e a listagem de `bob` autenticado contém exatamente 1 Tarefa, com `id` 43.
9. THE suíte de testes SHALL incluir um teste em que `bob` autenticado consulta `GET /api/tasks/42` enviando o proprietário `alice` pelo cliente (como parâmetro de consulta e, em outro caso, como cabeçalho) e recebe status HTTP 404, e em que `alice` autenticada consulta `GET /api/tasks/42` enviando o proprietário `bob` da mesma forma e recebe status HTTP 200 com `id` igual a 42.
10. WHEN o teste de caracterização `caracterizacao_BOLA_bobAcessaTarefa42DeAlice_retorna200` é atualizado, THE suíte de testes SHALL substituí-lo por um teste que espera status HTTP 404 para `bob` consultando a Tarefa 42, e a suíte SHALL NOT conter mais nenhum teste que espere status HTTP 200 para esse cenário.
11. THE suíte de testes SHALL preservar os controles de segurança ativos, sem desabilitar autenticação, autorização ou CSRF, sem remover ou relaxar asserções existentes e sem alterar o resultado esperado de um teste existente para fazê-lo passar.
12. IF qualquer teste de autorização desta suíte falhar, THEN THE suíte de testes SHALL reportar a execução como falha, sem ignorar, pular nem marcar o teste como desabilitado.

### Requirement 13: Governança e revisão humana

**User Story:** Como responsável pelo projeto, quero revisão humana antes de mudanças de segurança, para que alterações sensíveis sejam validadas.

#### Acceptance Criteria

1. WHEN uma alteração cria, modifica ou remove código de segurança (autenticação, autorização, verificação de owner, validação de entrada, configuração de segurança ou testes de autorização), THE equipe de desenvolvimento SHALL obter aprovação explícita de um revisor humano antes de aplicar a alteração ao código-fonte.
2. IF a aprovação humana explícita não tiver sido obtida para uma alteração de segurança, THEN THE equipe de desenvolvimento SHALL manter a alteração não aplicada, sem modificar nenhum arquivo, e informar ao solicitante que a aprovação está pendente.
3. THE equipe de desenvolvimento SHALL conduzir a correção sem executar deploy e sem acessar serviços externos.
4. WHEN um comando de terminal for proposto, THE equipe de desenvolvimento SHALL apresentar o comando completo a um revisor humano e obter aprovação explícita antes de executá-lo.
5. IF um comando de terminal proposto for rejeitado ou não receber aprovação, THEN THE equipe de desenvolvimento SHALL NOT executar o comando e SHALL manter o estado do projeto inalterado.
6. IF um comando proposto realizar deploy ou acessar serviço externo, THEN THE equipe de desenvolvimento SHALL rejeitar o comando, mesmo que haja aprovação humana, e SHALL informar o motivo da rejeição ao solicitante.