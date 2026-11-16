# Pontos de moderação e recursos

## Estado de publicação

O schema abaixo está preparado em `src/main/resources/db/manual/20261112_moderation_points_appeals.sql`, mas ainda não foi aplicado. O flag `MODERATION_POINTS_ENABLED` tem default `false`; não o habilite antes de aplicar e validar a migração no ambiente autorizado. A migração M5 de denúncias permanece independente e não deve ser repetida.

Não há backfill: decisões UPHOLD históricas permanecem sem pontos. Apenas novas decisões realizadas com o módulo habilitado criam um lançamento. Relatórios agrupados geram uma decisão e um lançamento, nunca um por denunciante.

## Pontuação e restrições

- Um UPHOLD atribui de 1 a 3 pontos ao autor persistido do `Post`, ou à própria ONG quando o alvo é OSC. A outra parte não recebe pontos.
- Ajustes manuais aceitam delta inteiro entre -10 e 10, exceto zero, exigem justificativa e são permitidos somente a `SUPER_ADMIN`. O saldo não pode ficar negativo; o score é `BIGINT` e pode superar 10.
- Ajustes manuais exigem uma chave UUID de idempotencia. Repetir a mesma chave com os mesmos dados retorna o lancamento original; reutiliza-la com outro conteudo gera conflito.
- Cinco pontos iniciam uma restrição temporária de 14 dias. Os pontos permanecem depois de sua expiração.
- Dez pontos ativam restrição permanente de publicação e alteração.
- A primeira passagem de menos de 5 para 5 ou mais inicia um único período temporário, inclusive em salto direto para 10+. Recalcular ou adicionar/remover pontos dentro da mesma faixa não reinicia a data.
- Ao cair abaixo de 10, a restrição permanente por pontos é removida. Ao cair abaixo de 5, o período temporário é limpo. Restrições administrativas, de denúncia, conta inativa e arquivo são independentes.
- A aplicação consulta horário atual para expiração; não depende de job agendado.

## Recursos

O autor de publicação, ou CRIADOR/ADMINISTRADOR atual da ONG sancionada, pode contestar uma decisão UPHOLD com justificativa de até 2.000 caracteres. A política implementada permite **um recurso por decisão durante toda a vida da decisão**, inclusive depois de recurso rejeitado. A unicidade protege contra replay e fila duplicada. A reversão aceita quantidade parcial ou integral, nunca superior aos pontos não revertidos da decisão. Uma decisão aceita adiciona lançamento compensatório; histórico original nunca é apagado.

Quem tomou a decisão original não pode analisar o recurso. Recursos de ONG verificam a associação e o cargo atual ao submeter; decisões administrativas exigem sessão privilegiada independente e `APPEAL_RESOLVE`.

O identificador numerico do sujeito usuario fica como snapshot no ledger/recurso, sem FK que impe?a a exclusao de conta apos a retencao estabelecida. O estado temporario atual e removido em cascata; ledger, decisao e recurso permanecem.

## Matriz de permissões

| Perfil | Entidades permitidas | Consultar pontuação | Ler recursos | Decidir recurso | Ajustar pontos |
|---|---|---:|---:|---:|---:|
| SUPER_ADMIN | ONG, conteúdo, usuário | Sim | Sim | Sim | Sim |
| PLATFORM_ADMIN | ONG, conteúdo, usuário | Sim | Sim | Sim | Não |
| MODERATOR | Fila e ações de denúncia existentes | Não | Não | Não | Não |
| AUDITOR | ONG, conteúdo, usuário | Sim, leitura | Sim | Não | Não |

Doações aparecem como indisponíveis no painel: não há integração financeira compatível nesta entrega. Nenhum fluxo Stripe/financeiro é chamado.

## Limites de cobertura de mutações

O serviço central de elegibilidade é aplicado nas rotas ordinárias de cadastro/edição/arquivamento de ONG, gerenciamento de membros, status iniciado pela ONG, criação/edição/remoção de publicações da ONG e candidatura/decisão de voluntariado. O bloqueio de usuário é global ao autor; o bloqueio de ONG é específico da organização. Administração de plataforma mantém suas permissões próprias, mas a sessão administrativa não substitui a autorização comum de autoria/membership nas operações ordinárias.

Login, leitura, logout, recuperação e alteração de credencial não consultam restrição de pontos. Rotas específicas de administração continuam protegidas por RBAC e sessão administrativa.

## Migração e validação pendentes

Aplicar a nova migração somente após revisão separada, com `ddl-auto=validate`, feature flag desligado e backup confirmado. Os testes PostgreSQL de concorrência, constraints, RLS, recursos e cobertura HTTP que dependem das tabelas novas devem ser executados depois da aplicação, em banco TEST sintético. A migração contém transação, preflight de objetos-base/one-shot, lock/statement timeouts, ledger append-only e revoga acesso direto às roles REST.
