# Auditoria técnica do Porto Ajuda

**Data:** 2026-10-28  
**Status:** Parcial — inventário e verificações locais concluídos; os fluxos com banco remoto e serviços externos não foram executados.

## Resumo executivo

O repositório contém um frontend estático em HTML, CSS e JavaScript, configurado para a Vercel, e uma API Java 21 com Spring Boot, Spring Security, JPA e PostgreSQL. A API usa cookies JWT, Resend para e-mail, Supabase Storage para imagens, CNPJA para validação de CNPJ e Nominatim para geocodificação.

Foram executados 59 testes unitários e de fatia web, todos aprovados. As 20 páginas HTML do diretório `public` responderam com HTTP 200 em um servidor estático local. Uma consulta somente de leitura à API local retornou `REMOTO` como estado do banco; por isso, nenhum fluxo de escrita foi executado contra essa API.

Foram corrigidos quatro problemas no código: o contrato entre o mapeamento JPA e a migração de causas, a condição de inclusão de membros, a permissão da rota `/cadastro-osc` e referências CSS/JavaScript vazias na página de login da OSC. A auditoria de dependências também levou a atualizações pontuais para versões corrigidas. A migração de banco continua manual e não foi aplicada.

O sistema não pode ser declarado validado de ponta a ponta. O ambiente acessível pela API local usa banco remoto, e os fluxos que enviam e-mail, consultam serviços externos, gravam no banco ou movimentam dinheiro ficaram sem teste ativo. Também há funcionalidades descritas em `requisitos` que ainda não têm integração com a API.

## Arquitetura e ambientes identificados

| Parte | Evidência no repositório | Resultado |
|---|---|---|
| Frontend | `public/pages`, `public/components`, `public/styles` | HTML/CSS/JavaScript estático; `vercel.json` publica `public` e define rewrites. |
| Domínio do frontend | `CNAME` | O arquivo aponta para `portoajuda.com.br`. |
| URL padrão de confirmação | `backend/src/main/resources/application.properties` | O padrão de `APP_FRONTEND_URL` usa `https://www.portoajuda.com.br`; confirme no painel/DNS se `www` e o domínio raiz estão ambos configurados. |
| URL da API no frontend | `public/components/modoBack.js` | `localhost` usa `http://localhost:8080`; os demais hosts usam `https://api.portoajuda.com.br`. |
| Backend | `backend/pom.xml` | Java 21; Spring Boot atualizado nesta auditoria de 4.0.6 para 4.0.9. |
| Banco | `backend/src/main/java/com/portoajuda/aplicacao_osc/config/DatabaseConfig.java` | PostgreSQL; tenta `DB_REMOTE_*` primeiro e só tenta `DB_LOCAL_*` se o remoto falhar. |
| Configuração local | `.env` e `application.properties` | `.env` é ignorado pelo Git e importado por `optional:file:../.env[.properties]`. Os nomes das variáveis foram verificados sem revelar valores. |
| Homologação | Configuração versionada | Não encontrei perfil, URL ou configuração que identifique uma homologação separada. |
| Hospedagem do backend | Configuração versionada | Não encontrei configuração de Railway ou de outro provedor nem logs de implantação no repositório. |
| Migrações | `backend/src/main/resources/db/manual` | Scripts SQL manuais; `spring.jpa.hibernate.ddl-auto=validate`. Não há Flyway ou Liquibase. |

O `.env` local contém configurações para banco remoto e local, chave JWT, Resend, Supabase, Cloudinary e allowlist CORS. **Os valores não foram lidos nem copiados para este relatório.** A API local respondeu `database-status=REMOTO` em `GET http://127.0.0.1:8080/api/config/database`; esse endpoint retorna somente o enum de estado. Um PostgreSQL também está escutando em `localhost:5432`, mas sua finalidade e seus dados não foram verificados.

O código usa Resend, Supabase, CNPJA e Nominatim. Há dependências e variável de ambiente de Cloudinary, mas não encontrei uso de Cloudinary no código de execução; os uploads atuais passam por Supabase. O frontend não tem `package.json` nem lockfile. Alguns recursos vêm de CDNs, incluindo QRCode.js e Leaflet.

## Mapa de funcionalidades

| Funcionalidade | Página/endpoint encontrado | Verificação | Resultado |
|---|---|---|---|
| Páginas públicas e arquivos estáticos | 18 páginas de produto em `public/pages` e 2 páginas Doom | GET local de cada HTML; verificação dos `href`/`src` locais | **Aprovado no nível estático:** 20/20 páginas responderam 200. Restou um link local quebrado para `recuperar-senha.html`. Não foi feita navegação interativa em navegador. |
| Rewrites do frontend | `vercel.json` | Leitura da configuração e comparação com os links locais | **Parcial:** rewrites como `/login` e `/oscs` estão mapeados; Vercel/DNS em produção não foram consultados. |
| Página de perfil | `/perfil`, `/usuario/perfil` | `PageAccessSecurityTest` | **Aprovado:** HTML público; API de perfil retorna 401 sem autenticação. |
| Cadastro de OSC | `cadastro-osc.html`, `/osc/create` | Testes unitários de DTO e serviço; MockMvc da rota pública | **Parcial:** validações e serviços foram exercitados com mocks. CNPJA, Nominatim, Supabase e persistência PostgreSQL não foram chamados. |
| Lista, mapa e perfil público de OSC | `/osc/list`, `/osc/{id}`, `/proximidade` | Testes de DTO e controller; leitura do fluxo frontend | **Parcial:** contratos e mapeamento de dados têm testes. Não foram consultados dados da API que usa o banco remoto. |
| Cadastro, login, sessão, confirmação e logout de usuário | `/usuario/register`, `/login`, `/refresh`, `/logout`, `/confirmar-email`, `/trocar-codigo` | Testes existentes de CSRF, filtro JWT, sessão e exclusão; leitura de controllers | **Parcial:** testes unitários e MockMvc passaram. Cadastro real, login com conta, e-mail e persistência não foram executados. |
| Perfil e acessibilidade | `/usuario/perfil`, `/usuario/preferencias`, `/usuario/foto-perfil` | Leitura do frontend/backend; testes CSRF para upload | **Parcial:** proteção CSRF do upload foi exercitada; não houve leitura/gravação em banco nem upload para Supabase. |
| Contato | `/contato` → Resend | Leitura do controller e validações | **Bloqueado por segurança operacional:** nenhum e-mail foi enviado. |
| Apoio e doações | `apoie.html`, `doacao-ong.html`, `contribuicao.html`, `infos-pagamento.html`, `confirm-pagamento.html` | Leitura de páginas e endpoints existentes | **Parcial/incompleto:** a página de apoio gera instruções/código Pix localmente. Não encontrei gateway, webhook ou endpoint de pagamento; não houve transação financeira. |
| Posts, vagas e voluntariado | Requisitos em `requisitos`; CRUD da home em `public/components/crud.js`; entidades e repositórios Java | Comparação entre páginas, controllers e chamadas da API | **Não integrado:** a home grava posts no IndexedDB do navegador. Não encontrei controllers/endpoints para posts, vagas ou candidaturas, nem páginas dedicadas de Posts e Voluntariado. |
| Área/login da OSC | `login-osc.html`, `login-osc.js` | Leitura do fluxo | **Reprovado como autenticação:** após validar o formato do CNPJ, o JavaScript redireciona para a home sem chamar a API. O login não autentica nem cria sessão. |
| Recuperação de senha da OSC | Link em `login-osc.html` | Checagem de arquivos/rotas | **Reprovado:** o link aponta para `recuperar-senha.html`, arquivo inexistente; não encontrei endpoint de redefinição de senha. |
| Página institucional e conteúdo complementar | Sobre, Iniciativas, Apoie, Contato, página molde | GET local das páginas | **Aprovado no nível estático;** interações e links externos não foram testados em navegador. |

### Rotas e endpoints encontrados

Os controllers implementam páginas e API para `/usuario`, `/osc`, `/contato` e `/api/config`. Entre os endpoints estão autenticação/refresh/logout, confirmação de e-mail, perfil e preferências, upload de foto, criação/edição/listagem/perfil de OSC, consulta de CNPJ, membros e contato. Não encontrei endpoints para CRUD de posts, vagas, candidaturas ou pagamentos.

O padrão de autorização exige autenticação para as demais rotas, com endpoints públicos para autenticação, páginas, lista e perfil público de OSC, consulta de CNPJ e configuração de estado do banco. CSRF está habilitado para métodos que alteram estado.

## Achados e correções

### AUD-01 — Migração de causas não correspondia ao mapeamento JPA

- **Gravidade:** Alta antes da correção; risco de indisponibilidade na publicação caso o script fosse aplicado como estava.
- **Evidência:** `Osc` mapeia `osc_causas` como coleção de `CausaOscRegistro`, com colunas `causa` e `causa_principal`. A migração inicialmente criava `causas_secundarias JSONB` e não criava `causa`. Com `ddl-auto=validate`, os contratos não eram compatíveis.
- **Correção:** Atualizei `20261027_reestruturar_osc_causas.sql` para criar as colunas compatíveis com a entidade e transformar as causas preservando a tabela anterior, com preflight, postflight e locks.
- **Reteste:** A consistência entre o mapeamento e o DDL foi conferida estaticamente. A migração **não foi executada** e não foi validada em PostgreSQL isolado ou remoto; a implantação continua pendente dessa validação.

### AUD-02 — Versões Maven com advisories OSV

- **Gravidade:** Alta para as versões originais enquanto permanecessem em uso; corrigida no POM.
- **Evidência:** Uma consulta OSV às dependências Maven resolvidas inicialmente retornou 79 correspondências de advisory em 129 artefatos. Entre os pacotes atingidos estavam Spring Framework, Tomcat, Jackson, PostgreSQL JDBC, Bouncy Castle e Apache HttpComponents. As faixas de correção foram conferidas nos advisories.
- **Correção:** Atualizei o parent Spring Boot para 4.0.9; Bouncy Castle para 1.85; e alinhei Apache HttpClient 5 para 5.6.5 e HttpCore/HTTP2 para 5.4.4. A árvore resolvida confirma essas versões, Jackson 2.21.7/3.1.7, Tomcat 11.0.27, Spring 7.0.10 e PostgreSQL JDBC 42.7.14.
- **Reteste:** A suíte segura passou com o novo conjunto. Uma nova consulta OSV sobre 126 artefatos Maven de runtime retornou **zero correspondências**. A consulta não cobre dependências de teste nem scripts/CDNs do frontend.

### AUD-03 — Cadastro público de OSC retornava 401 no backend

- **Gravidade:** Média, defeito funcional de rota local/backend.
- **Evidência:** `PageController` e `vercel.json` usam `/cadastro-osc`, enquanto `SecurityConfiguration` liberava `/cadastro-ong`. Um novo teste MockMvc reproduziu 401 antes da correção.
- **Correção:** Corrigi o matcher para `/cadastro-osc` e acrescentei teste de acesso público.
- **Reteste:** O teste específico passou e a suíte final passou.

### AUD-04 — Inclusão de membro tinha condição invertida

- **Gravidade:** Média, bloqueava usuários sem vínculo e aceitava a condição oposta.
- **Evidência:** `usuarioIsMember` retorna verdadeiro quando já existe vínculo em `OscMembros`, mas `addMembro` lançava erro quando o resultado era falso.
- **Correção:** Inverti a condição e acrescentei testes para novo membro e duplicidade em `OscServiceMembroTest`.
- **Reteste:** Os dois testes novos passaram; a suíte final passou.

### AUD-05 — Execução local pode usar banco remoto

- **Gravidade:** Alta, dependente de o banco remoto conter dados reais.
- **Evidência:** `DatabaseConfig` tenta `DB_REMOTE_*` antes de `DB_LOCAL_*`; o endpoint local de estado respondeu `REMOTO`. `DataInitializer` pode gravar papéis ao subir a aplicação. A configuração não seleciona o banco por perfil explícito.
- **Impacto:** Executar a aplicação local ou um teste de contexto completo pode conectar ao banco remoto e gravar dados. Esse comportamento não foi alterado nesta auditoria porque a separação local/homologação/produção precisa ser definida pelo responsável.
- **Ação recomendada:** Usar perfis explícitos, priorizar banco local isolado em desenvolvimento e impedir que testes completos usem credenciais remotas.

### AUD-06 — Login de OSC não autentica

- **Gravidade:** Média, fluxo anunciado mas não implementado.
- **Evidência:** `login-osc.js` valida o formato do CNPJ e redireciona diretamente para a home. Não chama endpoint de autenticação; não encontrei login de OSC por CNPJ no backend.
- **Impacto:** O usuário pode interpretar o redirecionamento como login concluído, embora nenhuma sessão seja criada.
- **Ação recomendada:** Implementar autenticação de OSC com sessão/autorizações compatíveis ou remover/desativar o fluxo até ele existir. A decisão de UX e o desenho de identidade não foram presumidos.

### AUD-07 — Falta de limitação em operações públicas

- **Gravidade:** Média, risco de abuso não testado.
- **Evidência:** Não encontrei limitador de taxa em login, reenvio de confirmação, `/osc/consultar-cnpj` e `/contato`. O contato chama Resend para um destino configurado.
- **Impacto:** Automação pode aumentar tentativas de senha, chamadas externas ou envio de e-mail. Não executei testes ativos nem enviei mensagens.
- **Ação recomendada:** Adicionar limites por IP/conta e controles antiabuso compatíveis com múltiplas instâncias, especialmente no contato e autenticação.

### AUD-08 — Funcionalidades planejadas ainda não têm fluxo backend

- **Gravidade:** Informativa/escopo funcional.
- **Evidência:** `requisitos` descreve posts, oportunidades de voluntariado, painel da OSC e ações de doação. A home tem CRUD no IndexedDB do navegador, e o backend possui entidades/repositórios, mas não controllers/endpoints para esses fluxos.
- **Impacto:** As alterações locais do navegador não são persistidas no PostgreSQL nem compartilhadas com outros usuários; não há fluxo completo de publicação, candidatura ou pagamento.
- **Ação recomendada:** Definir quais funcionalidades estão no escopo atual e implementar os endpoints/fluxos correspondentes, ou atualizar os requisitos para refletir o produto entregue.

### AUD-09 — Link de recuperação inexistente e dados locais interpolados em HTML

- **Gravidade:** Baixa; pendência funcional e de robustez.
- **Evidência:** `login-osc.html` aponta para arquivo inexistente `recuperar-senha.html`. Além disso, o CRUD local de posts usa `innerHTML` com títulos e nomes guardados no IndexedDB.
- **Impacto:** O link quebra. A interpolação pode executar HTML/script inserido pelo próprio usuário naquele navegador; não encontrei persistência/remessa desses campos pelo backend, portanto não confirmei XSS entre usuários.
- **Ação recomendada:** Implementar recuperação de senha com token de uso único e limites de envio; escapar os valores do CRUD local ou substituir as concatenações por APIs DOM seguras.

## Matriz de segurança

| Categoria | Resultado e evidência | Gravidade | Situação |
|---|---|---|---|
| JWT e sessão | Access token de 15 minutos vinculado a sessão; refresh token aleatório armazenado em hash; cookies HttpOnly e configuração Secure/SameSite. | — | Revisado em código; não houve login live. |
| CSRF | `CookieCsrfTokenRepository`; testes rejeitam token ausente/incorreto e permitem upload autenticado com token emitido. | — | Aprovado nos testes MockMvc. |
| Autorização | Rotas privadas exigem autenticação e endpoints OSC têm `@PreAuthorize`; `/usuario/perfil` permanece privado embora a página seja pública. | — | Testes de página/segurança passaram; dados reais não foram acessados. |
| CORS | Allowlist vem de `ALLOWED_ORIGINS`, credenciais habilitadas e origens exatas. | — | Configuração revisada sem expor valores; allowlist de produção não foi testada. |
| Uploads | Limite configurado de 10 MB e tipos JPEG/PNG/WebP; envio ocorre por Supabase no backend. | Baixa/informativa | Revisado estaticamente; upload externo não executado. |
| Abuso de endpoints | Sem rate limiting identificado para login, reenvio, consulta CNPJ e contato. | Média | Pendente; sem teste ativo. |
| Conteúdo do CRUD local | Campos do IndexedDB são concatenados em `innerHTML`. | Baixa | Risco restrito ao navegador local pelo fluxo encontrado; recomendada correção. |
| Dependências Maven | Consulta OSV: 79 correspondências antes das atualizações; zero após atualizações em 126 dependências de runtime. | — | Corrigido no POM e reconsultado. |
| Dependências JS/CDN | Sem manifest/lockfile de frontend; QRCode.js vem do jsDelivr sem `integrity`; Leaflet vem de CDN. | Baixa/informativa | Sem varredura OSV para scripts/CDN e sem teste de disponibilidade dos terceiros. |
| Configuração de banco | API local reportou `REMOTO`; configuração local tenta remoto antes do local. | Alta, se remoto for produção | Não houve escrita; requer separação de ambientes. |

## Testes automatizados e verificações

| Comando/verificação | Ambiente | Resultado |
|---|---|---|
| `mvn -f backend/pom.xml "-Dtest=ValidacaoCnpjOscServiceTest,UsuarioServiceExclusaoTest,SessaoUsuarioDesativadoTest,OscServiceUpdateTest,OscServiceCreateTest,OscServiceMembroTest,OscPersistenciaServiceTest,LimpezaServiceExclusaoTest,PageAccessSecurityTest,JwtAuthFilterTest,OscPerfilDTOTest,OscListagemDTOTest,RequestOscDTOTest,OscPerfilControllerTest,FotoPerfilCsrfTest,AuthControllerTest" test` | MockMvc/unitários locais; sem contexto completo e sem banco | **59 testes, 0 falhas, 0 erros, 0 ignorados.** |
| `mvn -f backend/pom.xml dependency:tree "-Dscope=runtime"` | Maven local | **BUILD SUCCESS**; árvore final conferida. |
| Consulta `https://api.osv.dev/v1/querybatch` | Coordenadas Maven resolvidas; não envia dados do projeto | **0 advisories para 126 artefatos Maven de runtime** após as atualizações. |
| Servidor `python -m http.server 8765 --bind 127.0.0.1 --directory public`; GET de todos os HTML | Loopback local | **20 páginas, 20 respostas 200.** Servidor encerrado após a verificação. |
| Verificação estática de `href`/`src` | Arquivos locais e rewrites de `vercel.json` | **1 link quebrado:** `pages/login-osc.html` → `recuperar-senha.html`. |
| `PageAccessSecurityTest#cadastroOscTambemEUmaPaginaPublica` | MockMvc | Antes da correção: 401. Depois da correção: 200 e forward para `pages/cadastro-osc.html`. |
| `GET http://127.0.0.1:8080/api/config/database` | API local, somente leitura | Respondeu `REMOTO`; nenhuma outra chamada à API foi realizada. |

Não executei `AplicacaoOscApplicationTests`: o teste sobe o contexto completo, que pode conectar ao banco remoto e executar `DataInitializer`. Também não executei `OscPostgresPersistenceTest`: é opt-in e usa `DB_REMOTE_URL`/porta 6543. Os testes CNPJA, Nominatim, Resend, Supabase, pagamentos e registros reais não foram chamados.

O Maven emitiu um aviso de classe duplicada `org.json.JSONObject` no classpath de teste (`org.json` e `android-json`). Não foi observado erro nos 59 testes, mas a combinação deve ser revisada se algum teste passar a depender dessa classe.

## Arquivos alterados nesta auditoria

| Arquivo | Alteração e motivo |
|---|---|
| [backend/pom.xml](backend/pom.xml) | Spring Boot 4.0.9, Bouncy Castle 1.85 e versões corrigidas do Apache HttpComponents após advisories OSV. |
| [backend/src/main/java/com/portoajuda/aplicacao_osc/segurity/SecurityConfiguration.java](backend/src/main/java/com/portoajuda/aplicacao_osc/segurity/SecurityConfiguration.java) | Liberada a rota pública correta `/cadastro-osc`. |
| [backend/src/main/java/com/portoajuda/aplicacao_osc/service/OscService.java](backend/src/main/java/com/portoajuda/aplicacao_osc/service/OscService.java) | Corrigida condição de usuário que já pertence a uma OSC. |
| [backend/src/test/java/com/portoajuda/aplicacao_osc/service/OscServiceMembroTest.java](backend/src/test/java/com/portoajuda/aplicacao_osc/service/OscServiceMembroTest.java) | Dois testes para inclusão e duplicidade de membro. |
| [backend/src/test/java/com/portoajuda/aplicacao_osc/segurity/PageAccessSecurityTest.java](backend/src/test/java/com/portoajuda/aplicacao_osc/segurity/PageAccessSecurityTest.java) | Teste para a rota pública de cadastro de OSC. |
| [backend/src/main/resources/db/manual/20261027_reestruturar_osc_causas.sql](backend/src/main/resources/db/manual/20261027_reestruturar_osc_causas.sql) | DDL e cópia ajustados ao mapeamento JPA existente. **Script não executado.** |
| [public/pages/login-osc.html](public/pages/login-osc.html) | Removidas referências a arquivos `.css` e `.js` vazios/inexistentes. |
| [AUDITORIA_TECNICA.md](AUDITORIA_TECNICA.md) | Relatório desta auditoria. |

Ao iniciar, `main` estava um commit atrás de `origin/main`, com 22 arquivos rastreados modificados e 8 arquivos não rastreados. Essas alterações foram preservadas; a auditoria não sincronizou a branch.

## Pendências e plano de ação

1. Confirmar que `DB_REMOTE_*` aponta para um ambiente de homologação autorizado; trocar o desenvolvimento local para perfil/banco local isolado. Não executar testes de escrita até essa confirmação.
2. Validar `20261027_reestruturar_osc_causas.sql` em uma cópia isolada do PostgreSQL, revisar o preflight e obter backup antes de qualquer aplicação remota.
3. Definir e implementar autenticação e recuperação de senha para OSCs; atualmente o formulário não cria sessão.
4. Adicionar limites de taxa e controles antiabuso para login, reenvio de confirmação, consulta de CNPJ e contato.
5. Definir se posts, vagas, candidaturas e pagamentos estão no escopo atual; implementar APIs e persistência ou atualizar os requisitos.
6. Consertar o destino de recuperação de senha e escapar os campos do CRUD local antes de inseri-los no DOM.
7. Confirmar os domínios apex/`www`, URLs e certificados na Vercel e no provedor da API; essas configurações remotas não estão no repositório.
8. Repetir fluxos completos de navegador e integrações somente em homologação isolada, com contas de teste, e-mail controlado e serviços externos autorizados.

## Limitações

- Não foi feito teste ativo contra produção, Vercel ou API pública.
- O processo local da API usa banco `REMOTO`; nenhuma escrita ou leitura de registros foi feita.
- Não foi identificada uma instância PostgreSQL de teste isolada nem inspecionado esquema/dados do banco.
- A migração manual não foi aplicada nem validada em PostgreSQL.
- A ferramenta de navegador integrada não abriu uma página local; a verificação do frontend ficou limitada a GETs estáticos e análise de caminhos.
- Não foram executadas transações financeiras nem enviados e-mails.
- A consulta OSV cobriu dependências Maven de runtime; não cobriu dependências de teste, scripts de CDN ou recursos servidos por terceiros.

Por essas limitações, a auditoria documenta resultados reais e bloqueios, mas **não atesta cobertura funcional total nem segurança de produção**.
