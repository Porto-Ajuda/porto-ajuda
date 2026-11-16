# Proteções contra abuso de autenticação

Os limites abaixo são aplicados pelo backend. Os contadores reutilizam `admin_auth_controls`, seus tipos já permitidos (`START_IP`, `START_ACCOUNT`, `ACCOUNT_FAILURE`), a gravação atômica por `INSERT ... ON CONFLICT` e as chaves HMAC de `AdminAuthCodeSecurity`. Os identificadores recebem namespace de fluxo antes do HMAC; nenhum e-mail ou IP é persistido em claro nessa tabela, e nenhuma migration é necessária.

## Fluxos de usuário

| Operação | Limite | Comportamento |
| --- | --- | --- |
| Login por senha | 60 tentativas por IP em 15 min; 5 falhas por conta em 15 min | O bucket por IP conta tentativas válidas e inválidas para limitar consultas concorrentes. O bucket de falhas é por e-mail normalizado e só é criado para contas existentes. A quinta falha bloqueia senhas incorretas por 15 min; a senha correta ainda autentica o titular e a janela expira sozinha e um login válido limpa as falhas da conta. Contas desconhecidas usam a limitação por IP e comparação com hash fictício. |
| Troca do código de login por sessão | 60 tentativas por IP em 15 min | Códigos aleatórios de 256 bits continuam consumidos atomicamente uma vez e expiram em 5 min; o limite adicional protege o endpoint público de abuso de recursos. |
| Cadastro | 10 solicitações por IP em 1 h | A 11ª solicitação é limitada. A resposta não contém identificadores ou detalhes do provedor. Conflitos de CPF/e-mail usam uma mensagem genérica. |
| Reenvio da confirmação de e-mail | 10 solicitações por IP e 3 por conta pendente em 1 h | O limite por conta só é criado após encontrar uma conta pendente; contas desconhecidas e confirmadas recebem a mesma resposta pública e não provocam envio. |
| Reativação de conta solicitada para exclusão | 60 tentativas por IP em 15 min e 10 por conta conhecida em 1 h | Mantém a reativação existente dentro do prazo de seis meses. E-mails desconhecidos não criam buckets por conta; recebem somente o limite por IP e a mesma resposta de falha. |
| Recuperação de senha | 3 solicitações por e-mail ou IP em 1 h; 10 tentativas de redefinição por IP em 1 h | Aplica-se também a e-mails desconhecidos. O token expira em 20 min, é armazenado somente como hash e só pode ser consumido uma vez. |

O limite de login por IP é uma reserva atômica antes da consulta da conta. Assim, tentativas simultâneas do mesmo IP não ultrapassam o bucket. As falhas da conta são gravadas em uma transação independente, então a exceção de credencial inválida não desfaz o contador. A janela finita evita bloqueio permanente por contador antigo.

## Fluxos compartilhados e administrativos

- MFA administrativo: até 3 inícios por conta e 20 por IP em 15 min; 5 falhas consecutivas por conta bloqueiam por 1 h; cada desafio dura 5 min e aceita até 5 tentativas por canal. O IP também tem bucket de verificação de 60 tentativas por janela de 15 min.
- Verificação de telefone: até 3 emissões por conta e 10 por IP em 1 h; o desafio expira em 5 min, aceita até 5 tentativas e a verificação por IP bloqueia após 30 falhas na janela de 1 h.
- O reenvio de código de login não é um gerador de OTP curto: o código existente tem 256 bits, expira em 5 min e é consumido atomicamente uma vez. A confirmação de e-mail não emite um novo código quando o link é repetido.
- `ClientIpResolver` usa `getRemoteAddr()` por padrão. `X-Real-IP` só é aceito quando `RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED` ou a propriedade equivalente habilita explicitamente o proxy confiável; `Forwarded` e `X-Forwarded-For` não são usados.

## Persistência e retenção

Os buckets da MFA, verificação telefônica, login, cadastro e reenvio usam chaves HMAC na tabela existente `admin_auth_controls`. Janelas são reiniciadas atomicamente pelo upsert; bloqueios têm prazo. Um job separado remove apenas buckets sem bloqueio ativo e sem atualização há mais de 30 dias. O padrão é diariamente às 03:25 em `America/Sao_Paulo`; `APP_ADMIN_AUTH_CONTROLS_CLEANUP_CRON=-` desabilita somente esse job. Ele não apaga contas, sessões, registros de recuperação nem eventos de auditoria.

O rate limit de recuperação usa `recuperacoes_senha`, com chaves HMAC de e-mail/IP, e remove registros operacionais com mais de 24 h durante solicitações. Não houve alteração de schema para esses limites.

## Comportamento de resposta e entrega

Login retorna a mesma falha para senha incorreta, conta inexistente, bloqueio temporário e conta inativa/não confirmada. O reenvio de confirmação e a recuperação usam respostas genéricas; exceções do provedor de e-mail não são devolvidas com detalhes ao cliente. O envio de confirmação no cadastro falha com mensagem genérica se o provedor não aceitar o envio, sem registrar token ou corpo do erro.

Esses limites reduzem abuso automatizado; não provam identidade nem substituem controle no proxy, monitoramento de tráfego distribuído ou políticas externas de entrega.
