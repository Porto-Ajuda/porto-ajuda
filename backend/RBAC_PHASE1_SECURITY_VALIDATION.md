# RBAC administrativo — validação final de segurança e PostgreSQL

**Data da validação:** 7–8 de novembro de 2026
**Resultado:** a migração manual pode avançar após o preflight no ambiente alvo. Nenhuma operação de produção foi executada.

## Escopo e decisão

Validei o código RBAC local, os dois scripts SQL manuais, o procedimento de bootstrap e o JAR empacotado. Usei uma instância PostgreSQL 18.6 nova, em `127.0.0.1:55432`, dentro de uma pasta temporária, com bancos descartáveis e credenciais de teste. O JAR foi iniciado com diretório de trabalho temporário e configuração explícita; nenhum `.env` do projeto foi carregado ou consultado. Não acessei Supabase, Railway nem outro banco compartilhado.

O enforcement continua desligado por padrão em [application.properties](./src/main/resources/application.properties). A conclusão é de **prontidão para migração manual**, não de autorização para deploy ou ativação automática. O preflight de produção ainda precisa confirmar os privilégios e a propriedade reais do schema, pois esse ambiente não foi acessado.

## Defeitos confirmados e corrigidos

1. **Expressões `@PreAuthorize` não encontravam o bean.** O bean era registrado com nome padrão `adminAuthorizationService`, enquanto as expressões referenciavam `@adminAuthorization`; requests com sessão falhavam em runtime. O serviço agora declara esse nome explicitamente em [AdminAuthorizationService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAuthorizationService.java), com teste de regressão.
2. **O driver PostgreSQL não inferia o tipo JDBC de `Instant` na auditoria.** Isso fazia uma operação administrativa falhar ao persistir seu evento. [AdminOperationAuditService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminOperationAuditService.java) agora vincula `Timestamp`; a escrita real foi validada no PostgreSQL.
3. **Falhas ao consultar atribuições não eram uma decisão de autorização explícita.** [AdminAuthorizationService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAuthorizationService.java) agora nega quando a tabela está ausente ou indisponível, sem fallback para `ROLE_ADMIN`.
4. **A proteção do último SUPER_ADMIN não preservava necessariamente um backup capaz de autenticar por MFA.** O invariante agora mantém tanto um SUPER_ADMIN ativo/configurado quanto um SUPER_ADMIN elegível ao fluxo MFA, inclusive telefone brasileiro válido. A contagem e a serialização foram testadas em PostgreSQL com conexões distintas.
5. **O bootstrap podia continuar após falha no preflight do `psql`, e o padrão de ticket era mais amplo que o documentado.** [20261107_admin_rbac_bootstrap_super_admin.sql](./src/main/resources/db/manual/20261107_admin_rbac_bootstrap_super_admin.sql) ativa `ON_ERROR_STOP` antes do preflight e limita a referência a `PA-` seguida de dígitos. Os cenários de sucesso e falha foram reexecutados depois dessa correção.

## Comportamento exato do feature flag

| Situação | Enforcement desativado (`false`) | Enforcement ativado (`true`) |
|---|---|---|
| Base de acesso a `/admin` | Usuário ativo com `ROLE_ADMIN` **e** sessão `ADMIN_SESSION` emitida após o fluxo privilegiado existente. Um JWT comum com `ROLE_ADMIN` não satisfaz o filtro. | Exige a mesma base, cookie e registro de sessão válidos, detalhes de sessão confiáveis e atribuição ativa consultada no banco. |
| Atribuição ausente, revogada ou tabela RBAC indisponível | A autorização legada não consulta a tabela RBAC. | Nega. Erro de acesso ao repositório também resulta em negação; a sessão apresentada é tratada como inelegível pelo serviço de sessão. |
| Rotas de atribuições | Negadas: `ADMIN_MANAGE` não faz parte do conjunto legado e os métodos de mutação verificam o flag antes de alterar dados. | Só SUPER_ADMIN recebe `ADMIN_MANAGE`; os serviços revalidam a permissão e impedem alteração própria. |
| `/admin/auth/capabilities` | Negada; não fabrica perfil para a sessão legada. | Retorna catálogo calculado pelo backend a partir da atribuição atual; a resposta não é controlada pelo frontend. |
| Inicialização sem tabelas RBAC | Compatível com `ddl-auto=validate`: o acesso JDBC às tabelas é tardio e os caminhos do flag desligado não consultam o repositório. | A aplicação pode iniciar, mas operações com sessão sem atribuição falham fechadas. Não se deve ativar antes da migração e dos bootstraps aprovados. |
| Auditoria de operações RBAC | O escritor de auditoria operacional RBAC não persiste eventos enquanto o flag estiver desligado; a auditoria de autenticação existente continua separada. | Mutação coberta, evento de negócio e revogação de sessões participam da mesma transação. Falha de auditoria cancela a mutação. |

O filtro administrativo limpa o contexto herdado e autentica somente pelo cookie `PA_ADMIN_SESSION` validado no servidor. O filtro JWT ignora `/admin`; o filtro administrativo também não se aplica aos dois endpoints públicos de início/verificação MFA nem ao logout, que revoga o hash do cookie apresentado. O CSRF continua configurado na cadeia de segurança. O fluxo continua com senha, OTP por e-mail e OTP por SMS; não houve troca do mecanismo, do cookie nem dos prazos (20 minutos de inatividade e duas horas absolutas).

## Rotas existentes verificadas

O teste de autorização ocorre no controller e, para mutações, novamente no serviço de domínio. `403` representa permissão insuficiente; uma resposta de domínio `404` no teste de OSC inexistente confirmou que a autorização da rota havia sido ultrapassada antes da busca do recurso.

| Método e rota | Flag desligado | Flag ligado | Serviço e observação de domínio |
|---|---|---|---|
| `POST /admin/auth/challenges` | Fluxo administrativo legado, sem consulta de atribuição. | Exige conta elegível com `ROLE_ADMIN`, atribuição ativa e contato elegível antes de iniciar os dois OTPs. | [AdminAuthenticationService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminAuthenticationService.java) e [AdminAuthDeliveryQueueService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminAuthDeliveryQueueService.java). |
| `POST /admin/auth/challenges/{challengeId}/verify` | Verifica os dois OTPs e emite a sessão existente. | Revalida elegibilidade/atribuição antes de concluir; alteração de conta invalida o desafio. | Emissão continua em [AdminPrivilegedSessionService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminPrivilegedSessionService.java), com o cookie já existente. |
| `GET /admin/auth/session` | Sessão privilegiada legada válida. | Sessão e atribuição ativa. | Sem informação de perfil controlada pelo cliente; resposta sem cache. |
| `GET /admin/auth/capabilities` | `403`. | Sessão válida e permissões atuais do perfil do banco. | Única fonte para capabilities é o servidor; envio de `X-Admin-Profile` não muda o resultado. |
| `POST /admin/auth/logout` | Revoga a credencial apresentada e limpa o cookie. | Mesmo comportamento. | Idempotência não concede acesso a outros endpoints. |
| `POST /admin/auth/revoke-all` | Permite revogação das próprias sessões pela autorização legada. | `SESSION_REVOKE_SELF`, disponível aos quatro perfis. | Não permite revogar sessões de terceiros. |
| `GET /admin/assignments` | `403`; não lista atribuições. | `SUPER_ADMIN` somente. | Paginação limitada; dados da atribuição são consultados no backend. |
| `POST /admin/assignments` | Negado; nenhuma atribuição é criada. | `SUPER_ADMIN` somente, sem autopromoção; conta alvo ativa com contatos verificados/elegíveis e justificativa. Se faltar, o serviço adiciona `ROLE_ADMIN` na mesma operação autorizada. | [AdminAssignmentService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentService.java) grava auditoria e revoga sessões na transação. |
| `PATCH /admin/assignments/{assignmentId}/profile` | Negado. | `SUPER_ADMIN` somente; sem autodemissão/autoalteração e com proteção do último Super. | Perfil anterior é revogado e o novo é criado atomicamente; sessões da conta alvo são invalidadas. |
| `POST /admin/assignments/{assignmentId}/revoke` | Negado. | `SUPER_ADMIN` somente; sem autorrevogação e sem remover o último Super elegível. | Revoga atribuição e `ROLE_ADMIN`, invalida sessões e grava evento na mesma transação. |
| `GET /admin/dashboard/overview` | Sessão administrativa legada. | Os quatro perfis (`DASHBOARD_READ`). | [AdminDashboardService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminDashboardService.java) retorna contagens e rótulos/horários de atividade de segurança, não PII de usuário nem valores financeiros. Resposta `no-store`. |
| `PATCH /admin/oscs/{id}/status` | Acesso legado amplo com sessão administrativa. | SUPER_ADMIN/PLATFORM_ADMIN podem desativar e reativar; restauração de arquivada exige SUPER_ADMIN. | [OscStatusService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/OscStatusService.java) bloqueia transição inválida, exige justificativa e grava histórico/auditoria. O status arquivado só restaura para `ATIVA`. |
| `GET /admin/oscs/{id}/perfil-privado` | Acesso legado com sessão administrativa. | SUPER_ADMIN/PLATFORM_ADMIN (`ORG_READ_PRIVATE`); Moderator/Auditor recebem negação. | [OscService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/OscService.java) retorna dados institucionais de contato/localização; a rota usa `no-store`. |
| `POST /admin/posts/create` | Acesso legado com sessão administrativa. | SUPER_ADMIN (`CONTENT_PUBLISH`) somente; conteúdo precisa ser notícia e ter justificativa. | [PostService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/PostService.java) verifica permissão novamente no serviço. |
| `PUT /admin/posts/put` | Acesso legado com sessão administrativa. | SUPER_ADMIN (`CONTENT_EDIT_NEWS`) somente; limita edição a notícias e exige justificativa. | Não oferece edição administrativa de conteúdo OSC/voluntariado. |
| `DELETE /admin/posts/delete` | Acesso legado com sessão administrativa. | SUPER_ADMIN (`CONTENT_DELETE`) somente; exige justificativa. | Exclusão é física e remove eventual vaga associada; permanece uma operação destrutiva, restrita e auditada. |
| `GET /admin/posts/meus-posts`, `GET /admin/posts/all` | Acesso legado com sessão administrativa. | Os quatro perfis (`CONTENT_READ`). | Retornos `no-store`; o controller passa por `CONTENT_READ`. |

O filtro não transforma um JWT normal em sessão privilegiada. As rotas comuns mantêm seu escopo de conta/OSC: `POST/PUT/DELETE /post/*` exigem `ROLE_OSC` e validam vínculo com a própria OSC; `PATCH /osc/{id}/status` exige cargo de criador/administrador na organização; a reativação comum continua bloqueada quando a origem foi `PORTO_AJUDA` ou indeterminada. `POST /usuario/telefone/verificacao` e `POST /usuario/telefone/verificacao/{challengeId}` usam o `id` do principal autenticado e operam somente na conta própria. Um usuário que também possua membership `ROLE_OSC` mantém apenas as capacidades normais dessa organização; membership/cargo de OSC não concede perfil nem permissão administrativa de plataforma.

## Matriz do catálogo e limites de implementação

| Área/permissão | SUPER_ADMIN | PLATFORM_ADMIN | MODERATOR | AUDITOR | Endpoint administrativo implementado nesta fase |
|---|:---:|:---:|:---:|:---:|---|
| Dashboard | Sim | Sim | Sim | Sim | `GET /admin/dashboard/overview` |
| Leitura operacional de OSC (`ORG_READ`) | Sim | Sim | Sim | Sim | Sem rota própria de listagem administrativa; perfil público continua público. |
| Perfil privado de OSC | Sim | Sim | Não | Não | `GET /admin/oscs/{id}/perfil-privado` |
| Desativar/reativar OSC | Sim | Sim | Não | Não | `PATCH /admin/oscs/{id}/status` |
| Restaurar OSC arquivada / arquivar | Sim / Sim | Não / Não | Não | Não | Restauração existe no PATCH; não há endpoint administrativo de arquivamento. |
| Ler/suspender/restaurar usuários | Sim / Sim / Sim | Sim / Sim / Sim | Não / Não / Não | Sim / Não / Não | Não há módulo administrativo de usuário nesta fase. Exclusão iniciada pelo próprio usuário não é suspensão administrativa. |
| Ler conteúdo | Sim | Sim | Sim | Sim | `GET /admin/posts/meus-posts` e `/all` |
| Desativar conteúdo / reativar conteúdo | Sim / Sim | Sim / Sim | Sim / Não | Não / Não | `CONTENT_DEACTIVATE` existe no catálogo, mas não há endpoint administrativo para moderação de publicação ou oportunidade. |
| Publicar notícias / editar notícia / excluir fisicamente | Sim | Não | Não | Não | `/admin/posts/create`, `/put`, `/delete` |
| Denúncias: ler/revisar/resolver | Sim / Sim / Sim | Sim / Sim / Sim | Sim / Sim / Não | Sim / Não / Não | Módulo/rotas não existem no backend inspecionado. |
| Apelações: ler/resolver | Sim / Sim | Sim / Sim | Não / Não | Sim / Não | Módulo/rotas não existem. |
| Auditoria: global / de caso | Sim / Sim | Não / Sim | Não / Sim | Sim / Não | Nenhum endpoint de consulta da auditoria operacional RBAC existe. |
| Gestão de atribuições / configuração crítica | Sim / Sim | Não / Não | Não / Não | Não / Não | Atribuições implementadas; configuração crítica não tem endpoint. |
| Resumo financeiro | Sim | Não | Não | Sim | Permissão reservada; sem endpoint. O dashboard não expõe totais financeiros. |
| Revogar próprias sessões | Sim | Sim | Sim | Sim | `POST /admin/auth/revoke-all` |

O catálogo está em [AdminPermissionCatalog.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminPermissionCatalog.java); os nomes de permissão ficam em [AdminPermission.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminPermission.java). A existência de uma permissão no catálogo não cria uma rota. Em especial, a moderação administrativa de publicação/vaga, os endpoints de usuário, denúncias, apelações, consulta de auditoria, resumo financeiro e configuração crítica continuam ausentes. Também não há metadado/rota de moderação que permita demonstrar origem de uma restrição de conteúdo e impedir reversão por Moderator. Essas capacidades não podem ser apresentadas como implementadas nesta fase.

## Validação PostgreSQL, bootstrap e concorrência

### Migração `20261107_admin_rbac_phase1.sql`

O banco de teste foi montado a partir do schema JPA existente e dos pré-requisitos M3/M4, sem usar `.env`. Apliquei a migração exata em PostgreSQL 18.6 e verifiquei:

- objetos em `public`: `admin_assignments`, `admin_operation_audit_events`, função e trigger de append-only;
- tipos e constraints de perfil/status, ciclo de vida, motivo, versões e snapshots; FK de `usuario_id` para `usuarios(id)` com `ON DELETE SET NULL`;
- índice único parcial de uma atribuição ativa por usuário e índices de consulta de perfil, alteração, evento, alvo e ator;
- tabelas com RLS habilitado e sem policy; sem privilégios de tabela para `anon`/`authenticated`; proprietário e privilégios do papel de teste conferidos;
- trigger que recusa `UPDATE`, `DELETE` e `TRUNCATE` no log operacional;
- preflight, postflight e ausência de alterações parciais após falha injetada antes do `COMMIT`; uma segunda execução foi recusada pelo preflight.

A migração tem transação única, `lock_timeout` de 5 s e `statement_timeout` de 5 min. O preflight exige executor `postgres` com `BYPASSRLS`, sem `SUPERUSER`, propriedade/ACL esperadas das tabelas base e roles `anon`/`authenticated`. Esses requisitos passaram no cluster isolado. **Não posso confirmar que o proprietário, ACLs e papel de execução do banco de produção correspondam**; o script foi feito para falhar antes de alterar schema se não corresponderem. As roles de owner/superuser também podem contornar RLS e trigger por privilégios de banco; o processo operacional deve proteger esse acesso.

### Bootstrap manual

Depois de corrigir o script, executei o corpo SQL real via `psql`, substituindo apenas as duas entradas interativas `\prompt` por variáveis controladas de teste. Resultados:

- conta explicitamente selecionada, ativa, verificada, com role `ADMIN` e telefone MFA elegível: aceita;
- referência `SEC-123`, sem `ADMIN`, conta inativa e conta inexistente: recusadas;
- repetição para usuário que já tem atribuição ativa: recusada sem duplicar;
- segunda conta elegível selecionada individualmente: aceita; contagem aumentou exatamente uma atribuição por execução; não houve promoção em massa;
- trigger de teste que falha ao inserir o evento de bootstrap: atribuição e evento permaneceram ausentes após rollback;
- preflight em banco sem o schema-base: saída não zero antes da execução do corpo.

O bootstrap não é migration/startup automático e não emite sessão. A pessoa ainda precisa completar o fluxo normal de MFA. O script exige referência `PA-` com dígitos e grava somente essa referência como justificativa do bootstrap.

### Último SUPER_ADMIN e auditoria atômica

[AdminAssignmentRepository.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentRepository.java) usa `pg_advisory_xact_lock(1346458434, 1)`. [AdminRbacInvariantService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminRbacInvariantService.java) exige transação existente; atribuição, perfil, revogação, mudanças que retiram elegibilidade, exclusão pelo usuário e rotinas de limpeza chamam o mesmo lock antes da mutação. A remoção de `ROLE_ADMIN` ocorre nos fluxos de revogação/limpeza protegidos. Mudança administrativa no banco feita diretamente fora da aplicação não participa desse protocolo e deve ser tratada como operação de emergência controlada.

O teste real abriu duas conexões PostgreSQL, confirmou a espera do segundo backend em `Lock/advisory`, concluiu a primeira alteração e verificou a rejeição da concorrente; sobrou um Super ativo/elegível. Outro teste confirmou que uma conta ativa, mas sem telefone elegível para MFA, não serve como backup de sessão. Isso valida o lock no PostgreSQL real, não apenas por mocks.

As mutações de atribuição passam por [AdminAssignmentService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentService.java); o escritor JDBC [AdminOperationAuditService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminOperationAuditService.java) participa da transação. Em falha forçada de persistência no endpoint real de revogação, o serviço respondeu erro e mantiveram-se a atribuição ativa, `ROLE_ADMIN`, sessões privilegiadas e contagens de auditoria anteriores. A falha de trigger no bootstrap também confirmou rollback. Um registro de sucesso foi consultado no PostgreSQL: padrões testados de cookie, Bearer, JWT, chave opaca e OTP não permaneceram no motivo. A sanitização é baseada em padrões reconhecíveis; operadores ainda não devem colar segredos em justificativas livres.

## Testes e execução do JAR

| Verificação | Resultado |
|---|---|
| 18 classes de teste RBAC/sessão/domínio, incluindo `AdminAuthorizationServiceTest`, `AdminAssignmentServiceTest`, `AdminRbacInvariantServiceTest`, `AdminRbacPostgresIntegrationTest`, filtro, MFA, OSC, posts, usuário e limpeza | **93 testes; 0 falhas, 0 erros, 0 ignorados.** |
| `mvnw.cmd -q -DskipTests package` após a correção do SQL manual | **Exit 0**, JAR atual reconstruído. |
| JAR atual, schema anterior à M5, flag `false`, `ddl-auto=validate` | Tomcat iniciou; `GET /admin/dashboard/overview` sem cookie retornou `401`. |
| JAR atual, schema M5 migrado, flag `false`, `ddl-auto=validate` | Tomcat iniciou; dashboard sem cookie retornou `401`. |
| JAR atual, M5 e bootstrap explícito, flag `true`, `ddl-auto=validate` | Tomcat iniciou; dashboard e capabilities sem cookie retornaram `401`. |
| Teste HTTP autenticado em JAR empacotado durante a validação da aplicação | SUPER_ADMIN recebeu capabilities; Moderator/Auditor foram recusados em atribuições, mudança de OSC e mutação destrutiva de post; Platform Admin passou o check de desativação de OSC; Auditor revogou somente a própria sessão; JWT sem cookie e usuário sem atribuição foram recusados; cabeçalho de perfil falso não mudou permissões. |

O `AdminRbacPostgresIntegrationTest` passou com 4 testes PostgreSQL. Os processos do JAR foram encerrados depois de cada verificação. A instância PostgreSQL temporária foi parada. A remoção dos dois diretórios temporários fora do workspace foi recusada pela revisão automática do host; os arquivos residuais permanecem na pasta temporária do agente e não são bancos em execução.

## Arquivos afetados nesta implementação e validação

**Fundação RBAC:** [AdminActor.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminActor.java), [AdminAssignment.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignment.java), [AdminAssignmentController.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentController.java), [AdminAssignmentDTO.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentDTO.java), [AdminAssignmentProfileRequest.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentProfileRequest.java), [AdminAssignmentRepository.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentRepository.java), [AdminAssignmentRequest.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentRequest.java), [AdminAssignmentRevokeRequest.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentRevokeRequest.java), [AdminAssignmentService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentService.java), [AdminAssignmentStatus.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentStatus.java), [AdminAuthenticationDetails.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAuthenticationDetails.java), [AdminAuthorizationService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminAuthorizationService.java), [AdminCapabilitiesDTO.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminCapabilitiesDTO.java), [AdminOperationAuditService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminOperationAuditService.java), [AdminPermission.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminPermission.java), [AdminPermissionCatalog.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminPermissionCatalog.java), [AdminProfile.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminProfile.java), [AdminRbacInvariantService.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminRbacInvariantService.java) e [AdminReasonSanitizer.java](./src/main/java/com/portoajuda/aplicacao_osc/admin/AdminReasonSanitizer.java).

**Controllers, filtros e serviços protegidos:** [AdminAuthController.java](./src/main/java/com/portoajuda/aplicacao_osc/controller/AdminAuthController.java), [AdminDashboardController.java](./src/main/java/com/portoajuda/aplicacao_osc/controller/AdminDashboardController.java), [AdminOscController.java](./src/main/java/com/portoajuda/aplicacao_osc/controller/AdminOscController.java), [AdminPostController.java](./src/main/java/com/portoajuda/aplicacao_osc/controller/AdminPostController.java), [AdminSessionAuthFilter.java](./src/main/java/com/portoajuda/aplicacao_osc/segurity/AdminSessionAuthFilter.java), [AdminAuthDeliveryQueueService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminAuthDeliveryQueueService.java), [AdminAuthenticationService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminAuthenticationService.java), [AdminDashboardService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminDashboardService.java), [AdminPrivilegedSessionService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/AdminPrivilegedSessionService.java), [LimpezaService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/LimpezaService.java), [OscService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/OscService.java), [OscStatusService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/OscStatusService.java), [PostService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/PostService.java), [UsuarioService.java](./src/main/java/com/portoajuda/aplicacao_osc/service/UsuarioService.java) e [application.properties](./src/main/resources/application.properties).

**SQL e operação:** [20261107_admin_rbac_phase1.sql](./src/main/resources/db/manual/20261107_admin_rbac_phase1.sql), [20261107_admin_rbac_bootstrap_super_admin.sql](./src/main/resources/db/manual/20261107_admin_rbac_bootstrap_super_admin.sql) e [RBAC_PHASE1_DEPLOYMENT.md](./RBAC_PHASE1_DEPLOYMENT.md).

**Testes adicionados:** [AdminAssignmentServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/admin/AdminAssignmentServiceTest.java), [AdminAuthorizationServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/admin/AdminAuthorizationServiceTest.java), [AdminPermissionCatalogTest.java](./src/test/java/com/portoajuda/aplicacao_osc/admin/AdminPermissionCatalogTest.java), [AdminRbacInvariantServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/admin/AdminRbacInvariantServiceTest.java), [AdminRbacPostgresIntegrationTest.java](./src/test/java/com/portoajuda/aplicacao_osc/admin/AdminRbacPostgresIntegrationTest.java), [AdminReasonSanitizerTest.java](./src/test/java/com/portoajuda/aplicacao_osc/admin/AdminReasonSanitizerTest.java). **Testes existentes alterados:** [AdminSessionAuthFilterTest.java](./src/test/java/com/portoajuda/aplicacao_osc/segurity/AdminSessionAuthFilterTest.java), [AdminAuthControllerSessionTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/AdminAuthControllerSessionTest.java), [AdminAuthenticationServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/AdminAuthenticationServiceTest.java), [AdminDashboardServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/AdminDashboardServiceTest.java), [AdminPrivilegedSessionServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/AdminPrivilegedSessionServiceTest.java), [LimpezaServiceExclusaoTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/LimpezaServiceExclusaoTest.java), [OscServiceAdminSessionTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/OscServiceAdminSessionTest.java), [OscServiceCreateTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/OscServiceCreateTest.java), [OscServiceUpdateTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/OscServiceUpdateTest.java), [OscStatusServiceTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/OscStatusServiceTest.java), [PostServiceAdminSessionTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/PostServiceAdminSessionTest.java), [UsuarioServiceAdminSessionRevocationTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/UsuarioServiceAdminSessionRevocationTest.java) e [UsuarioServiceExclusaoTest.java](./src/test/java/com/portoajuda/aplicacao_osc/service/UsuarioServiceExclusaoTest.java). Este relatório é [RBAC_PHASE1_SECURITY_VALIDATION.md](./RBAC_PHASE1_SECURITY_VALIDATION.md). `AUDITORIA_TECNICA.md` e `CONFIGURACAO.md` já estavam modificados antes desta validação e não foram alterados por ela. Nenhum arquivo frontend foi alterado.

## Sequência de ativação e recuperação

1. Faça backup recuperável e confirme janela, aprovadores e responsáveis do banco. Mantenha `ADMIN_RBAC_ENFORCEMENT_ENABLED=false`.
2. No ambiente alvo, confirme no preflight o papel `postgres` não-superusuário com `BYPASSRLS`, owner/ACL das tabelas base, schema `public` e roles `anon`/`authenticated`. Se qualquer condição divergir, pare e revise o script; não contorne o preflight.
3. Faça inventário de contas com role `ADMIN`, avalie elegibilidade de MFA e obtenha aprovação nominal de cada perfil. Não promova em massa.
4. Aplique manualmente uma vez a migração M5 revisada e confira o postflight, constraints, ownership, grants, RLS e trigger append-only.
5. Execute o bootstrap individualmente para **duas** contas SUPER_ADMIN aprovadas e elegíveis. Valide MFA e acesso das duas usando a aplicação atualizada com flag desligada. Não registre senha, OTP, cookie ou token no ticket.
6. Publique o artefato validado com flag desligada e confira login, MFA, CSRF, sessão, expiração e revogação legados.
7. Ative o flag. As duas contas Super permanecem autorizadas; outros administradores sem atribuição são negados. Um Super atribui perfis aprovados individualmente e testa cada capability antes de liberar a UI. Não exponha módulos ainda inexistentes.
8. Confirme logs de segurança, auditoria de operações e revogação de sessões. Preserve o segundo Super como acesso de contingência.

Se a ativação falhar, primeiro mantenha o serviço em flag desligada enquanto a causa é investigada e use um período curto, aprovado e monitorado: **desligar o flag reabre os privilégios legados de todas as contas ativas com ROLE_ADMIN e ADMIN_SESSION**. Nesse modo o escritor de auditoria operacional RBAC fica inativo, então registre a intervenção fora da aplicação. Corrija migração/atribuição com os responsáveis, valide novamente dois Super elegíveis e só então reative o flag. Não remova tabelas, não apague eventos e não faça alteração direta de `ROLE_ADMIN`/atribuições sem procedimento de emergência revisado.

## Riscos remanescentes

- Privilégios/owner e estado de dados do banco de produção não foram observados; o preflight deve ser revisado no momento da migração manual.
- O lock de último Super protege os caminhos da aplicação testados; uma escrita direta por owner/DBA fora da aplicação não adquire o advisory lock.
- Redação de justificativa detecta padrões comuns de credencial, OTP, cookie e token; não é possível reconhecer com certeza qualquer segredo arbitrário escrito como texto livre. Não cole credenciais em motivos.
- Usuários, denúncias, apelações, configuração, auditoria consultável, finanças e moderação própria de conteúdo/oportunidades não possuem rotas administrativas nesta fase. A matriz do catálogo não deve ser confundida com implementação de endpoints.
- A UI não consome `/admin/auth/capabilities`; a autorização do servidor protege as rotas, mas navegação permission-aware ainda falta.

## Conclusão

**READY FOR MANUAL PRODUCTION MIGRATION**

Esta decisão cobre somente a migração manual M5 e bootstraps individuais após os preflights locais e do ambiente alvo. Não autoriza deploy, habilitação automática do flag ou acesso de produção.
