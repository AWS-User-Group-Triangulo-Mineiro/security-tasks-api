---
inclusion: always
---
# Segurança do projeto
- Toda consulta de recurso deve respeitar a identidade autenticada.
- Nunca confiar em owner recebido do cliente.
- Usar DTOs e validação; não retornar entidades diretamente.
- Não incluir segredos, dados reais ou PII em logs e prompts.
- Incluir testes positivos e negativos de autorização.
- Não executar deploy; não acessar serviços externos; revisar comandos.
- Nunca desabilitar controles apenas para fazer testes passarem.
- Solicitar revisão antes de alterações em segurança.