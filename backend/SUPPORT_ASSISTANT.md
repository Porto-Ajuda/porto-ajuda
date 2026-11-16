# Assistente de atendimento

Primeira versão de conversas persistentes com assistência informativa e encaminhamento para atendimento humano. O assistente consulta apenas respostas públicas e pesquisas de conteúdo público; não possui acesso a perfis, documentos, candidaturas, finanças, SQL ou ferramentas de mutação.

## Ativação deliberada

O schema do assistente foi aplicado e validado no Supabase TEST pela migracao manual `src/main/resources/db/manual/20261115_support_assistant.sql`. A migration e de aplicacao unica; nao a execute novamente. O padrao do repositorio permanece desativado e a aplicacao exige ativacao deliberada por processo.

Flags do processo:

- `SUPPORT_ASSISTANT_ENABLED=false` — flag mestre, desativada por padrão.
- `SUPPORT_ASSISTANT_AI_ENABLED=false` — sem chamadas a provedor por padrão; conversas habilitadas sem IA são encaminhadas para a fila humana.
- `SUPPORT_ASSISTANT_AI_PROVIDER=gemini` e `SUPPORT_ASSISTANT_AI_MODEL` — escolha explícita do adaptador/modelo.
- `GEMINI_API_KEY` — credencial existente. Nunca é enviada ao navegador nem registrada em logs.

No launcher local de TEST, `SUPPORT_ASSISTANT_ENABLED=true` habilita conversas humanas e a rotina de retencao; `SUPPORT_ASSISTANT_AI_ENABLED=false` mantem mensagens na fila humana com explicacao ao usuario. A desativacao imediata e `SUPPORT_ASSISTANT_ENABLED=false`; a desativacao apenas da IA e `SUPPORT_ASSISTANT_AI_ENABLED=false`. Habilitar a IA exige decisao separada e pode consumir o orcamento compartilhado. O worker de documentos continua sujeito as flags ja existentes e nao e alterado aqui.

O adaptador usa o singleton `GeminiRequestBudget` já compartilhado com análise documental, sem orçamento independente. Os limites efetivos continuam sendo os do bean comum (padrões 10 requisições/minuto, 200 mil tokens de entrada/minuto e 450 requisições/dia, sempre limitados pelos tetos codificados). Não há fallback de provedor nem retry externo nesta primeira versão.

## Estados e permissões

- `AI_ACTIVE` → `WAITING_HUMAN` quando a pessoa solicita atendimento ou falta referência pública suficiente.
- `AI_ACTIVE`/`WAITING_HUMAN` → `HUMAN_ACTIVE` por atendente autorizado.
- `HUMAN_ACTIVE` → `AI_ACTIVE` ou `CLOSED` pela pessoa responsável.
- `CLOSED` pode ser reaberta pela própria pessoa solicitante.

`SUPER_ADMIN`, `PLATFORM_ADMIN` e `MODERATOR` recebem `SUPPORT_READ` e `SUPPORT_MANAGE`; `AUDITOR` não recebe acesso a conversas. O backend também valida a atribuição administrativa atual. Respostas humanas usam o rótulo público genérico “Equipe Porto Ajuda”.

## Privacidade e persistência

Mensagens são visíveis a administradores com `SUPPORT_READ`, conforme aviso exibido antes do envio. O adaptador recebe somente a conversa recente necessária e até cinco resultados públicos; credenciais, CPF provável, email e telefone detectáveis são redigidos antes do provedor. Conteúdo público recuperado é tratado como dado não confiável e o modelo não tem ferramentas.

Pedidos sobre dados pessoais, pagamentos/doações ou criação/edição de conteúdo são encaminhados para atendimento humano e não consultam o provedor.

Mensagens e eventos tÃªm retenÃ§Ã£o distinta: o conteÃºdo das conversas fechadas Ã© removido 30 dias apÃ³s o encerramento; reabrir cancela esse prazo e um novo encerramento inicia outro perÃ­odo. Conversas ativas e aguardando atendimento nÃ£o expiram por idade. ApÃ³s o descarte, permanecem somente metadados tÃ©cnicos de transiÃ§Ã£o, sem corpos de mensagem nem identificadores diretos copiados. A rotina de descarte Ã© ativada junto com `SUPPORT_ASSISTANT_ENABLED` e processa lotes limitados. Esta regra Ã© uma polÃ­tica do produto, nÃ£o uma garantia jurÃ­dica.

## Rotas

- Público e somente leitura: `GET /support-assistant/intro`, `GET /support-assistant/public-search`.
- Conversas do usuário autenticado: `/support-assistant/conversations/**`.
- Fila de equipe: `/admin/support-assistant/conversations/**`, sob a sessao MFA `PA_ADMIN_SESSION` e as permissoes `SUPPORT_READ`/`SUPPORT_MANAGE`.

Mutação continua protegida por CSRF e sessão normal; operações administrativas usam a sessão administrativa/RBAC existente.
