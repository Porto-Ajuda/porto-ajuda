# Configuração do backend

## Banco de dados

O backend usa uma única configuração PostgreSQL para execução local e Railway. O driver PostgreSQL conecta ao projeto Supabase existente; não há fallback para PostgreSQL local nem seleção por perfil.

Estas três variáveis são obrigatórias:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`

O Spring Boot cria o `DataSource` a partir dessas propriedades e exige SSL (`sslmode=require`). Se alguma variavel obrigatoria estiver ausente, a resolucao da configuracao falha durante a inicializacao informando seu nome. A aplicacao mantem `spring.jpa.hibernate.ddl-auto=validate`; o Hibernate valida o esquema e nao aplica migracoes nem cria ou altera tabelas.

`GET /api/config/database` informa o provedor configurado (`SUPABASE`); esse endpoint não verifica disponibilidade ou saúde da conexão.

## Execução local

Configure as tres variaveis no ambiente do processo ou em `.env` ignorado pelo Git na raiz. `application.properties` importa explicitamente `.env` do diretorio atual ou do diretorio pai, suportando inicializacao pela raiz do repositorio ou por `backend`; o ambiente do processo tambem e aceito.

Execute o backend a partir de `backend` com `mvnw.cmd spring-boot:run` no Windows ou `./mvnw spring-boot:run` em Unix. O backend usará as mesmas variáveis e o mesmo Supabase usados pelo deploy.

O `.env` usa exclusivamente `DB_URL`, `DB_USER` e `DB_PASSWORD` para o datasource. O usuario pelo pooler Supabase deve preservar o identificador de projeto exigido pelo provedor.

## Railway

O repositorio nao contem arquivo versionado de configuracao Railway. Cadastre `DB_URL`, `DB_USER` e `DB_PASSWORD` no painel de variaveis do servico. A execucao local e o deploy usam a mesma configuracao PostgreSQL, sem alternancia por perfil.

As outras variáveis obrigatórias da aplicação são `ALLOWED_ORIGINS`, `APP_FRONTEND_URL`, `APP_BACKEND_URL`, `RESEND_API_KEY`, `MAIL_FROM`, `SUPABASE_URL`, `SUPABASE_SECRET_KEY` e `SUPABASE_BUCKET_PUBLICO`. `PORT` e `APP_COOKIES_SECURE` têm valores padrão; `MAIL_TO` é opcional.

## SMS Mais

O backend envia SMS somente pelo servidor, usando `SMSMAIS_TOKEN`. `SMSMAIS_ENVIRONMENT` aceita `sandbox` ou `production` e assume `sandbox` quando omitido. No sandbox o backend exige token com prefixo `sbx_`; em produção rejeita tokens com esse prefixo. A SMS Mais documenta o mesmo endpoint para ambos os ambientes: tokens `sbx_` simulam a entrega sem enviar nem cobrar.

Opcionalmente, `SMSMAIS_CONNECT_TIMEOUT` e `SMSMAIS_READ_TIMEOUT` configuram os limites de conexão/resposta (padrões 3s e 5s). A ausência de token não impede o restante da aplicação de iniciar; uma tentativa de envio sem configuração falha com erro sanitizado. Timeout não é repetido automaticamente, pois o resultado remoto pode ser desconhecido; reenvios seguros devem reutilizar o mesmo `externalId`. O serviço apenas entrega o texto recebido e não gera nem valida códigos.
## Autenticação administrativa (fases 1 e 2)

Configure `ADMIN_AUTH_CODE_PEPPER` como segredo aleatório de pelo menos 32 bytes no ambiente do backend. Ele protege os HMACs dos OTPs/identificadores e deriva a chave AES-GCM do outbox. O fluxo falha fechado se o segredo não estiver disponível. Não grave esse valor no frontend ou em logs.

Os desafios e duas entregas são gravados atomicamente. Os códigos de entrega ficam cifrados com AES-GCM no outbox até o envio e são apagados após sucesso ou falha. Workers consultam o outbox com `FOR UPDATE SKIP LOCKED`, permitindo várias instâncias sem que elas consumam a mesma entrega. Se um processo cair depois de o provedor aceitar a mensagem, a mesma entrega/código pode ser reenviada após a concessão expirar; isso não cria um novo desafio.

O corpo/status do início não distingue conta elegível, e o envio externo ocorre por worker depois do commit. Isso reduz o vazamento de tempo causado pelos provedores, mas não promete tempo constante: consulta de usuário, verificação da senha, gravação/auditoria e carga do banco ainda diferem.

Por padrão, `RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED=false`, e o limitador usa o endereço do socket. Railway documenta `X-Real-IP` como o endereço observado no edge público e seu edge substitui cabeçalhos de entrada; para habilitar essa origem no serviço de produção, configure `RAILWAY_PUBLIC_EDGE_IP_HEADER_ENABLED=true` somente se todo tráfego de autenticação usar o ingresso HTTP público. O código ignora `X-Forwarded-For`, `Forwarded` e `CF-Connecting-IP`. Tráfego `*.railway.internal` não passa pelo edge e pode fornecer `X-Real-IP`; não habilite a confiança se esse tráfego puder chamar as rotas diretamente. Nenhuma variável do Railway foi alterada nesta tarefa. Railway informa que os detalhes do edge podem mudar; consulte [especificações de rede pública](https://docs.railway.com/networking/public-networking/specs-and-limits) antes de habilitar.

O SMS Mais entrega códigos da autenticação administrativa via worker; o backend continua gerando e validando os códigos.

Após a validação dos dois canais, o backend emite uma credencial aleatória de 256 bits. Só o SHA-256 dessa credencial é persistido. A credencial vai exclusivamente no cookie `PA_ADMIN_SESSION`, `HttpOnly`, com `Path=/admin` e validade de navegador limitada a 2 horas. Com `APP_COOKIES_SECURE=true` (padrão), o cookie usa `Secure; SameSite=None` para a futura origem cross-site; em desenvolvimento HTTP local, `APP_COOKIES_SECURE=false` produz `SameSite=Lax`. Não configure o modo inseguro em produção. No servidor, cada request administrativo revalida sessão, conta ativa e `ROLE_ADMIN`; a sessão expira após 20 minutos sem atividade ou ao completar 2 horas absolutas. Logout e revogação própria invalidam a credencial no banco e removem o mesmo cookie. Mudanças de senha/e-mail/telefone revogam sessões existentes; remoção de `ROLE_ADMIN` ou desativação bloqueia a próxima requisição administrativa e marca a sessão revogada.

Como os cookies são usados pelo frontend cross-site futuro, o cookie CSRF `XSRF-TOKEN` usa `SameSite=None` quando `APP_COOKIES_SECURE=true` e `Lax` no modo HTTP local; ele continua legível pelo cliente e não autentica. CSRF permanece habilitado em `/admin/**`, inclusive login MFA e logout. O frontend deve obter um token por `GET /usuario/csrf` e enviar o token no header indicado pela resposta (atualmente `X-XSRF-TOKEN`) em toda operação mutável. Não há exceção CSRF para rotas administrativas.

`M4` é a migração manual `db/manual/20261106_admin_privileged_sessions.sql`. Ela depende de M3 e não deve ser executada automaticamente. Não foram adicionadas variáveis de ambiente novas para sessão; `ADMIN_AUTH_CODE_PEPPER` continua obrigatório para a emissão/verificação MFA. `PA_ADMIN_SESSION` exige HTTPS porque é sempre `Secure`; não desative essa flag para testes ou deploy.
## Inicializadores e limpeza automática

- `DataInitializer` e `InitializeService` só criam os papéis `ADMIN`, `USUARIO` e `OSC` quando `APP_INITIALIZATION_SEED_ROLES=true`. A opção é falsa por padrão. O semeio consulta cada papel antes de inserir; habilite-a apenas de forma deliberada e em uma única instância.
- `LimpezaService.executarLimpezaAutomatica` só agenda a limpeza quando `APP_MAINTENANCE_CLEANUP_CRON` contém uma expressão cron explícita. Sem essa variável, a tarefa fica desativada. Ela pode excluir sessões inválidas, cadastros não confirmados e contas cujo prazo de exclusão venceu; não habilite em desenvolvimento local usando o banco compartilhado.
- Não há outro `CommandLineRunner` ou `ApplicationRunner` de inicialização de dados no backend.

## Logs e testes

SQL de aplicação não é exibido e os loggers de informações de conexão do Hibernate/Hikari ficam desativados. Credenciais não devem ser registradas nem colocadas em arquivos frontend. Não versione `.env`.

`AplicacaoOscApplicationTests` carrega o contexto Spring e pode conectar ao banco configurado; nao o execute para validar apenas esta configuracao. `OscPostgresPersistenceTest` e opt-in e recusa hosts que nao sejam loopback local; use somente PostgreSQL descartavel. Os testes isolados de contrato de configuracao nao inicializam contexto nem abrem conexao.
