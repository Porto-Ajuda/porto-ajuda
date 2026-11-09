# RBAC administrativo — fase 1

Este runbook descreve o deploy da implementação RBAC no backend e a ativação controlada do enforcement. O estado abaixo é específico da produção Supabase/Railway em 9 de novembro de 2026; as instruções para ambientes novos estão separadas ao final.

## Estado confirmado em produção

- A migração M5, `20261107_admin_rbac_phase1.sql`, foi aplicada com sucesso no Supabase.
- O bootstrap inicial foi executado. Os usuários `47` e `48` possuem atribuições `SUPER_ADMIN` ativas.
- Os dois usuários autenticaram localmente pelo fluxo MFA administrativo existente, receberam o perfil `SUPER_ADMIN` e 27 permissões. O logout invalidou a sessão e o endpoint de capabilities retornou HTTP 401 após o logout.
- O backend com a implementação RBAC ainda não foi implantado.
- `ADMIN_RBAC_ENFORCEMENT_ENABLED` deve permanecer `false` durante o deploy inicial e as verificações anteriores à ativação.

**Não reaplique M5 nem execute outro bootstrap na produção.** A propriedade `app.admin.rbac.enforcement-enabled` em `application.properties` usa `${ADMIN_RBAC_ENFORCEMENT_ENABLED:false}`; portanto, o padrão permanece desativado quando a variável não está definida.

## Conteúdo da fase

- Atribuição persistente única por usuário e perfil, consultada no banco a cada autorização.
- Catálogo de permissões em `AdminPermissionCatalog` para `SUPER_ADMIN`, `PLATFORM_ADMIN`, `MODERATOR` e `AUDITOR`.
- A checagem exige simultaneamente `ROLE_ADMIN`, `ADMIN_SESSION`, cookie de sessão validado e atribuição ativa quando o RBAC estiver habilitado.
- Revogação de todas as sessões privilegiadas após concessão, troca ou revogação de atribuição.
- Lock transacional global para preservar tanto o último `SUPER_ADMIN` ativo/configurado quanto o último elegível para sessão (conta ativa, `ROLE_ADMIN`, email e telefone verificados).
- Justificativa e evento de auditoria na mesma transação das mutações cobertas.
- Sanitização de justificativas para remover cookies, bearer/JWT, chaves e OTPs em formatos reconhecíveis; o bootstrap aceita apenas referências `PA-12345`.
- Nenhuma tela de gestão foi criada; as rotas de atribuição são API de backend e ficam negadas enquanto o flag estiver falso.

## Matriz de permissões preparada

`✓` indica permissão no catálogo central. Escopo do objeto e estado da entidade ainda são validados no serviço; a matriz não substitui essas regras.

| Área/operação | SUPER_ADMIN | PLATFORM_ADMIN | MODERATOR | AUDITOR |
|---|:---:|:---:|:---:|:---:|
| Dashboard | ✓ | ✓ | ✓ | ✓ |
| OSC: consulta operacional | ✓ | ✓ | ✓ | ✓ |
| OSC: consulta privada | ✓ | ✓ | — | — |
| OSC: desativar/reativar | ✓ | ✓ | — | — |
| OSC: restaurar arquivamento/arquivar | ✓ | — | — | — |
| Usuários: consultar | ✓ | ✓ | — | ✓ |
| Usuários: suspender/restaurar | ✓ | ✓ | — | — |
| Conteúdo: consultar | ✓ | ✓ | ✓ | ✓ |
| Conteúdo: desativar | ✓ | ✓ | ✓ | — |
| Conteúdo: reativar | ✓ | ✓ | — | — |
| Conteúdo: publicar/editar notícias/excluir | ✓ | — | — | — |
| Denúncias: consultar | ✓ | ✓ | ✓ | ✓ |
| Denúncias: investigar/revisar | ✓ | ✓ | ✓ | — |
| Denúncias: resolver | ✓ | ✓ | — | — |
| Recursos: consultar | ✓ | ✓ | — | ✓ |
| Recursos: resolver | ✓ | ✓ | — | — |
| Auditoria: global | ✓ | — | — | ✓ |
| Auditoria: eventos do caso | ✓ | ✓ | ✓ | — |
| Atribuições administrativas | ✓ | — | — | — |
| Configuração crítica | ✓ | — | — | — |
| Resumo financeiro agregado | ✓ | — | — | ✓ |
| Revogar as próprias sessões privilegiadas | ✓ | ✓ | ✓ | ✓ |

As memberships e cargos de OSC continuam no domínio da organização. Eles não concedem nem alteram `ROLE_ADMIN`, atribuições ou permissões da plataforma. A auditoria de escopo de caso, os módulos inexistentes e o redaction de dados são trabalho posterior antes de expor essas capacidades em telas.

Nenhum usuário existente com `ROLE_ADMIN` recebe perfil automaticamente. O bootstrap escolhe contas aprovadas individualmente, com contato verificado e telefone que passa pela mesma validação de MFA; demais administradores só entram após avaliação e atribuição explícita. Ao ativar o enforcement, uma sessão sem atribuição ativa deixa de ser aceita, mesmo que o usuário mantenha `ROLE_ADMIN`.

## Scripts SQL e ambientes

### Produção atual

- M5 e o bootstrap inicial estão concluídos. Não execute os arquivos `20261107_admin_rbac_phase1.sql` nem `20261107_admin_rbac_bootstrap_super_admin.sql` em produção.
- `20261108_supabase_sql_editor_bootstrap_super_admin_47_48.sql` é um artefato histórico do bootstrap de produção já executado. **Não execute nem reexecute esse arquivo. Exclua-o do commit da aplicação**; mantenha-o apenas onde a política operacional da equipe guarda scripts históricos.

### Ambientes novos

Para uma instalação nova, use uma cópia descartável compatível do schema e revise os scripts antes de qualquer operação persistente. `20261107_admin_rbac_phase1.sql` cria as tabelas, constraints, índices, RLS sem policies e proteção append-only da auditoria. Depois de validar a migração e obter aprovação operacional, `20261107_admin_rbac_bootstrap_super_admin.sql` concede um perfil inicial a uma conta selecionada e elegível. Execute esses scripts manualmente, uma única vez por ambiente, com backup e autorização. Não os inclua em build, startup ou deploy automático.

O bootstrap genérico não emite sessão: a pessoa ainda usa o fluxo existente de senha + OTP de email + OTP de SMS. Use uma referência de aprovação real no formato `PA-12345`; não grave senhas, OTPs, cookies ou tokens na justificativa.

## Sequência de deploy e ativação em produção

1. **Pré-commit:** confirme que os testes e a verificação PostgreSQL foram executados apenas em banco local descartável; revise `git diff`, `git diff --check`, arquivos não rastreados e configuração Railway. Exclua do commit da aplicação o bootstrap histórico específico dos usuários 47/48. Confirme que o valor de enforcement permanece `false`.
2. **Commit e push:** faça staging seletivo dos arquivos RBAC revisados, revise o diff staged e crie o commit. Faça push da branch aprovada para iniciar o fluxo normal de revisão/deploy. Não inclua `.env`, `target/`, credenciais nem o artefato histórico de bootstrap.
3. **Deploy inicial:** configure/ confirme em Railway `ADMIN_RBAC_ENFORCEMENT_ENABLED=false` antes do deploy. Publique o backend aprovado sem alterar qualquer SQL ou dado de produção.
4. **Startup e saúde:** confirme que a nova versão inicia, passa nos health checks da plataforma, conecta ao banco com `ddl-auto=validate` e não relata erro de schema ou falha de inicialização.
5. **MFA e sessões:** valide o fluxo administrativo existente com senha, OTP de email e OTP de SMS; confirme emissão e uso do cookie/sessão administrativa, expiração e revogação. Confirme que logout invalida a sessão e que a aplicação permanece com CSRF habilitado.
6. **Confirmação somente leitura das atribuições:** usando acesso de leitura aprovado no Supabase, confira que os usuários `47` e `48` têm exatamente uma atribuição `SUPER_ADMIN` com status `ACTIVE`. Confirme os dois eventos de bootstrap correspondentes. Consulte apenas IDs, perfil, status, ação e alvo; não selecione email, telefone ou outros dados pessoais. Não altere registros e não execute scripts de bootstrap. Se qualquer resultado divergir, mantenha o flag `false` e interrompa a ativação para investigação.
7. **Ativação controlada:** somente após startup, MFA/sessões e confirmação de dados, altere `ADMIN_RBAC_ENFORCEMENT_ENABLED=true` em Railway conforme aprovação operacional e reinicie/reimplante a versão. A ativação deve ser observada por responsável técnico e pelos dois administradores elegíveis.
8. **Verificação dos SUPER_ADMIN:** faça autenticação MFA dos usuários 47 e 48; confirme `/admin/auth/capabilities` com perfil `SUPER_ADMIN` e o catálogo esperado de 27 permissões. Confirme que logout ou revogação resulta em HTTP 401 para capabilities.
9. **Smoke tests de autorização e isolamento:** confirme que JWT comum com `ROLE_ADMIN`, sem sessão privilegiada/cookie válido, não acessa capabilities nem rotas protegidas; que sessão revogada é negada; que permissões dependem da atribuição atual no servidor; e que membership/cargos normais de OSC não concedem autoridade administrativa. Execute apenas operações não destrutivas ou previamente aprovadas.
10. **Rollback:** se a inicialização falhar antes da ativação, mantenha o flag `false` e reverta o deploy para o artefato anterior conforme o procedimento Railway. Se a falha surgir após ativar RBAC, primeiro avalie se é possível corrigir a configuração/atribuição mantendo o enforcement ligado. Se for necessário restaurar o serviço, defina `ADMIN_RBAC_ENFORCEMENT_ENABLED=false` e reinicie/reimplante conforme o procedimento operacional. Isso reabre temporariamente o acesso legado a usuários ativos com `ROLE_ADMIN` e sessão administrativa, sem aplicar os perfis; exija autorização de incidente, janela curta e monitoramento. Preserve as tabelas, atribuições e auditoria; não execute rollback de schema nem apague dados.

## Retorno seguro

Se a aplicação continuar saudável mas uma permissão estiver incorreta, mantenha o enforcement ativo, revogue sessões afetadas e corrija a atribuição com outro `SUPER_ADMIN`; preserve a auditoria. Se o serviço não puder operar, a recuperação pode voltar ao artefato e à política anteriores. **Desabilitar o flag reabre o acesso legado para todas as contas ativas com `ROLE_ADMIN` e sessão administrativa, sem aplicar os perfis**, por isso requer autorização de incidente, janela curta e monitoramento explícito. Não remova as tabelas nem apague atribuições ou eventos de auditoria. Reversão de schema exige plano separado e análise de dependências.

## Limites atuais

As rotas de atribuição, dashboard, consulta privada de OSC, estado de OSC e aliases administrativos de posts têm checks centrais. Módulos administrativos de usuários, denúncias, recursos, configuração e resumo financeiro não existem no backend inspecionado; o catálogo reserva permissões para eles, mas não cria endpoints. A moderação de publicações ainda precisa de metadados persistentes de origem/restrição para provar que um moderador não reverte uma restrição da plataforma. A interface administrativa ainda não consome capabilities.
