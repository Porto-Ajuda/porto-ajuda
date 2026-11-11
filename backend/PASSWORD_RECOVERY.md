# Recuperação e alteração de senha

## Configuração

Configure `PASSWORD_RECOVERY_PEPPER` como Base64 URL-safe sem padding, contendo pelo menos 32 bytes gerados aleatoriamente. Use o mesmo valor em todas as instâncias do backend. A aplicação falha no startup se a variável estiver ausente, malformada ou curta. O pepper protege as chaves HMAC de limitação por e-mail/IP; ele não é a chave de hash dos tokens e nunca deve ser registrado.

`APP_FRONTEND_URL` é a origem confiável usada para formar o link enviado por e-mail. Deve ser HTTPS fora de loopback; HTTP só é aceito para `localhost`, `127.0.0.1` e `::1`. O host do request não participa da composição do link.

No desenvolvimento local servido pelo Spring Boot, use `APP_FRONTEND_URL=http://localhost:8080`. O link canônico de recuperação usa `/login#redefinir-senha/<token>`; o fragmento é interpretado pelo JavaScript no navegador. Em produção, configure `APP_FRONTEND_URL=https://portoajuda.com.br` no ambiente do backend e publique os assets do frontend com a rota de recuperação antes de enviar links para essa origem. Mudanças locais não atualizam os assets publicados.

As rotas canônicas de página são `/login` e `/catalogo?categoria=<categoria>`. Os caminhos históricos `/pages/login.html` e `/pages/catalogo.html?categoria=<categoria>` continuam compatíveis. O Vercel usa rewrites para as rotas canônicas; Spring encaminha `/login` e `/catalogo` aos mesmos arquivos estáticos. O parâmetro `categoria` permanece na URL.

O rate limit usa o endereço obtido por `ClientIpResolver`. Por padrão usa apenas `getRemoteAddr()`. `RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED` só deve ser habilitada se o edge confiável sobrescrever o `X-Real-IP` documentado; `X-Forwarded-For` e `Forwarded` nunca são usados.

O envio usa as configurações existentes `RESEND_API_KEY` e `MAIL_FROM`. Testes devem substituir `EmailService` por mock ou capturar a chamada localmente; não use destinatários reais.

## Banco de dados

Antes de iniciar uma instalação com a funcionalidade, aplique uma vez `src/main/resources/db/manual/20261110_password_recovery.sql` em uma base descartável/local, usando o mesmo role owner do datasource do backend, e valide o schema com `ddl-auto=validate`. A migração é transacional e propositalmente não é idempotente: uma repetição falha no `CREATE TABLE`, fazendo rollback integral. Confirme a instalação existente antes de tentar novamente. Não execute pelo startup da aplicação.

A tabela guarda hashes de token, chaves HMAC para rate limiting e tentativas; nunca guarda o token bruto. Solicitações desconhecidas também deixam uma linha de rate limit com `usuario_id` e `token_hash` nulos. A limpeza ocorre ao receber solicitações novas, removendo registros com mais de um dia.

## Entrega de e-mail

O evento de e-mail é publicado após commit e processado por um executor limitado, para que o tempo de resposta não espere o provedor. Não há outbox nem retry durável: falha do processo, fila cheia ou erro do Resend pode deixar uma credencial válida sem e-mail entregue. A resposta pública não confirma entrega; o usuário pode solicitar novamente, o que invalida o link anterior. Uma futura fase pode adicionar outbox/retry persistente.

## Testes isolados

### Validação Supabase TEST explicitamente autorizada

`PasswordRecoverySupabaseIntegrationTest` é uma exceção opt-in para o ambiente de teste Supabase confirmado pelo operador. Ela permanece ignorada na suíte normal e só executa quando `PASSWORD_RECOVERY_TEST_DATABASE_CONFIRMED=true` está definido no processo. Antes de qualquer fixture, confirma que a URL efetiva é PostgreSQL, que o host identifica Supabase, que o banco é `postgres` e que `sslmode=require` está ativo. O datasource e as credenciais `DB_URL`, `DB_USER` e `DB_PASSWORD` devem ser fornecidos explicitamente no processo; não os grave neste documento, no script ou em arquivos do repositório.

O teste exige `PASSWORD_RECOVERY_TEST_LEDGER` apontando para um arquivo temporário fora do repositório. Cada ID de usuário sintético é registrado antes da limpeza; a remoção usa somente esses IDs e chaves HMAC exclusivas da fixture. Eventos de auditoria administrativa são mantidos para respeitar o histórico append-only. O teste substitui email/SMS por mocks, configura integrações de storage/pagamento com valores locais inertes e não habilita seed de roles nem tarefas de limpeza.

Defina no processo, sem persistir os valores: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PASSWORD_RECOVERY_TEST_DATABASE_CONFIRMED=true`, `PASSWORD_RECOVERY_TEST_LEDGER` (fora do checkout), `PASSWORD_RECOVERY_PEPPER` (aleatório Base64 URL-safe com ao menos 32 bytes), `JWT_SECRET_KEY` (aleatória Base64 padrão com pelo menos 32 bytes) e `ADMIN_AUTH_CODE_PEPPER` (aleatório com ao menos 32 bytes). O runner deve forçar `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, substituir configurações externas e não carregar credenciais do `.env` como fallback. Com essas variáveis já verificadas:

```powershell
cd backend
.\mvnw.cmd '-Dtest=PasswordRecoverySupabaseIntegrationTest' test
```

Esse teste não aplica migrações. A migração `20261110_password_recovery.sql` é de execução única: confirme por catálogos se já existe e valide sua estrutura; nunca a repita para testar rollback em um banco compartilhado.

Para os demais testes PostgreSQL, configure explicitamente uma base descartável em loopback, `portoajuda_password_test`, sem importar `.env`. A suíte do projeto usa a propriedade JVM `admin.ngo.pg.test.url` para outros testes locais; nao aponte propriedades de teste `DB_URL` para Supabase. Não use dados reais.
