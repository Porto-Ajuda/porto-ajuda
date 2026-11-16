# Denúncias e proteção preventiva de conteúdo

## Ativação

O fluxo permanece desligado por padrão: `CONTENT_REPORTING_ENABLED` ausente equivale a `false`. A migration `src/main/resources/db/manual/20261111_content_reporting_moderation.sql` foi aplicada e validada somente no Supabase TEST em 2026-11-12; ela continua one-shot e não deve ser executada novamente nesse banco. Não foi aplicada em produção. O teste integrado ativa a flag apenas nas propriedades do processo de teste; nenhuma configuração normal foi habilitada.

## Política padrão

| Alvo canônico | Limite | Janela móvel |
| --- | ---: | ---: |
| Publicação e oportunidade (mesmo ID de `post`) | 5 denunciantes distintos | 24 h |
| ONG | 10 denunciantes distintos | 48 h |

Somente denúncias `PENDING` dentro da janela contam. **A regra aprovada é no máximo uma denúncia `PENDING` por pessoa e alvo; depois da análise (`DISMISSED` ou `UPHELD`), a pessoa pode denunciar novamente quando sete dias completos tiverem passado desde `reviewed_at`. No instante exato de `reviewed_at + 7 dias`, a nova denúncia é elegível.** As denúncias anteriores e o histórico de revisão são preservados; uma nova denúncia cria uma nova linha. O índice único parcial no banco protege a concorrência de denúncias pendentes, enquanto o serviço serializa a verificação de cooldown e a inserção com o mesmo lock do alvo usado pela revisão. A identificação do denunciante é persistida como HMAC com a finalidade `content-report-reporter`, nunca como ID/email exposto no registro de denúncia ou na fila. `PUBLICACAO` e `VOLUNTARIADO` são validados contra o tipo real e armazenados como alvo `POST`, impedindo alias para contornar a contagem. Depois do limite, uma linha de proteção preventiva persistente oculta o alvo e o envia à fila; expirar a janela não reativa o conteúdo. A ONG/post e as candidaturas são preservadas.

O alvo de publicação é o `Post` público já existente (incluindo posts `DOACAO` que aparecem no visualizador genérico de publicações); não há denúncia de campanha financeira, pagamentos, Stripe ou dados de arrecadação nesta fase.

Os valores podem ser ajustados, dentro de limites seguros, por `CONTENT_REPORT_PUBLICATION_THRESHOLD`, `CONTENT_REPORT_VOLUNTEER_THRESHOLD`, `CONTENT_REPORT_ORGANIZATION_THRESHOLD`, `CONTENT_REPORT_PUBLICATION_WINDOW_HOURS`, `CONTENT_REPORT_VOLUNTEER_WINDOW_HOURS` e `CONTENT_REPORT_ORGANIZATION_WINDOW_HOURS`.

Antiflood: no máximo 5 tentativas por usuário e 30 por endereço de conexão em uma hora. Os contadores reutilizam `admin_auth_controls` e suas chaves HMAC. O controller usa `ClientIpResolver`: por padrão considera o peer TCP; somente `RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED=true` autoriza o `X-Real-IP` documentado, e `X-Forwarded-For`/`Forwarded` nunca são confiados. O limite expira com a janela, sem bloqueio indefinido da conta. A linha de controle permanece até a rotina existente de limpeza remover registros expirados.

O fingerprint HMAC do denunciante usa a finalidade `content-report-reporter` e o segredo já existente `ADMIN_AUTH_CODE_PEPPER` (mínimo de 32 bytes UTF-8); não há ID/email do denunciante nas tabelas de denúncia nem na API administrativa. O backend deve continuar usando a mesma chave entre reinícios para preservar deduplicação. Trocar essa chave sem re-fingerprinting quebra a política de “uma denúncia por pessoa/alvo para sempre” e pode permitir uma nova denúncia; a rotação exige procedimento de migração/rehash revisado, que esta fase não implementa. Os alvos polimórficos são verificados por trigger com lock de chave; alvos com histórico não podem ser fisicamente apagados, e a tela/serviços regulares continuam usando arquivamento/estado já existentes.

No preflight read-only, o guard append-only da M5 tinha `EXECUTE` direto para `anon`, `authenticated` e `service_role`. A migration de reporting revoga apenas essa chamada direta do trigger e não altera os privilégios de tabela M5; o trigger append-only existente continua anexado. As novas tabelas/sequences/funções de reporting ficam inacessíveis a esses três roles, e o backend segue usando o owner `postgres` conforme o modelo existente.

## Permissões administrativas

* `REPORT_READ`: fila e detalhes, inclusive AUDITOR em modo somente leitura.
* `REPORT_REVIEW`: MODERATOR, PLATFORM_ADMIN e SUPER_ADMIN podem revisar; todos podem descartar denúncias com justificativa.
* `REPORT_RESOLVE`: PLATFORM_ADMIN e SUPER_ADMIN podem confirmar/manter uma restrição independente.
* `CONTENT_REACTIVATE`: PLATFORM_ADMIN e SUPER_ADMIN podem encerrar uma restrição de denúncia ativa com justificativa. Isso não reativa ONG arquivada/inativa nem post que tenha outro estado de ocultação.

A autorização exige `ADMIN_SESSION`, atribuição ativa no banco e permissão de perfil. `ROLE_ADMIN` sem a sessão administrativa não atende aos endpoints.

## Limitações operacionais

O aviso ao criador da ONG é enviado depois do commit pelo `EmailService` existente e não inclui denunciantes. A tabela de fila é a fonte persistente de revisão; o envio não possui outbox/retry durável, portanto uma falha temporária de e-mail não remove a proteção, mas pode exigir contato operacional. A interface cobre relatório/revisão e não adiciona punição de conta, pontos, banimento, appeals ou denúncias de campanhas de doação.

## Testes PostgreSQL opt-in

O harness [ContentReportPostgresIntegrationTest.java](../src/test/java/com/portoajuda/aplicacao_osc/service/ContentReportPostgresIntegrationTest.java) usa a aplicação com `ddl-auto=validate`, fixtures sintéticas e mocks para e-mail/SMS/storage. Não executa resets, migrations ou `TRUNCATE`; a limpeza usa IDs registrados em `CONTENT_REPORTING_TEST_LEDGER`. Quando uma decisão gravar histórico imutável, os alvos sintéticos necessários permanecem ocultos por um bloqueio de fixture e sua retenção fica registrada no ledger.

O harness [ContentReportPostgresIntegrationTest.java](../src/test/java/com/portoajuda/aplicacao_osc/service/ContentReportPostgresIntegrationTest.java) foi executado contra o Supabase TEST após a aplicação da migration: 14 testes passaram, sem falhas, erros ou skips. Ele usa `ddl-auto=validate`, fixtures sintéticas e mocks de e-mail/SMS/storage; não executa resets, migrations ou `TRUNCATE`. A execução requer confirmação process-scoped `CONTENT_REPORTING_TEST_DATABASE_CONFIRMED=true`, `CONTENT_REPORTING_TEST_DB_HOST=<hostname exato do TEST>` e `CONTENT_REPORTING_TEST_LEDGER=<arquivo fora do repositório>`. A guarda compara o host ao datasource efetivo e exige o banco `postgres` e TLS. Não rode novamente a migration one-shot; rode o harness com `.\mvnw.cmd -Dtest=ContentReportPostgresIntegrationTest test` somente após confirmar o mesmo banco TEST. Os relatórios/revisões sintéticos necessários à história imutável permanecem ocultos e seus IDs ficam nos ledgers fora do repositório.
