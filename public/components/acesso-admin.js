import { getAPI_URL } from "./modoBack.js";
import { adminAuthFailureMessage, adminAuthNextFactor } from "./admin-auth-feedback.js";

(() => {
    'use strict';

    const CODIGO_ACESSO = 'IDDQD';

    // Evita inicializar o sistema mais de uma vez.
    if (window.__portoAcessoAdminInicializado) return;
    window.__portoAcessoAdminInicializado = true;

    function inicializarAcessoAdmin() {

        const API_URL = getAPI_URL();
        // API_URL escolhe o padrão de rotas existente; a navegação usa a origem atual do frontend.
        const ROTAS = API_URL === "http://localhost:8080"
            || API_URL === "https://api.portoajuda.com.br"
            ? { admin: "/admin" }
            : { admin: "../admin/index.html" };

        /* =========================================
           1. CRIAÇÃO AUTOMÁTICA DO MODAL
           ========================================= */

        if (!document.querySelector('#modal-acesso-admin')) {

            const conteudoModal = `

<dialog id="modal-acesso-admin">

    <canvas id="chuva-matrix" aria-hidden="true"></canvas>

    <div class="acesso-admin-painel">

        <div class="modal-header">
            <h1 class="modal-title">⚠ SISTEMA DE SEGURANÇA</h1>

            <button
                class="close-modal"
                id="fechar-acesso-admin"
                type="button"
                aria-label="Fechar"
            >
                <span class="material-symbols-outlined">close</span>
            </button>
        </div>

        <!-- ETAPA 1: AVISO DE ACESSO -->
        <section id="etapa-aviso-admin">

            <div class="modal-body">
                <h2>VOCÊ ESTÁ EM UMA ÁREA RESTRITA.</h2>

                <p>
                    Esta área exige autenticação administrativa.
                </p>

                <p>Continue somente se você estiver autorizado a acessar a administração.</p>

                <h2 class="alerta-acesso-admin">
                    TEM CERTEZA DE QUE DESEJA CONTINUAR?
                </h2>

                <p class="status-acesso-admin">
                    Aguardando sua decisão.
                </p>
            </div>

            <div class="modal-footer">
                <button
                    type="button"
                    class="confirm-modal"
                    id="continuar-acesso-admin"
                >
                    Continuar para o login administrativo
                </button>
            </div>

        </section>

        <!-- ETAPA 2: LOGIN ADMINISTRATIVO -->
        <section id="etapa-login-admin" hidden>

            <div class="modal-body">

                <div class="login-admin-identificacao">
                    <span>Autenticação necessária</span>
                    <span>Acesso administrativo</span>
                </div>

                <h2 class="titulo-login-admin">
                    Identifique-se
                </h2>

                <p class="descricao-login-admin">
                    Informe suas credenciais administrativas
                    para solicitar acesso ao sistema.
                </p>

                <form id="form-login-admin" autocomplete="on">

                    <div class="campo-login-admin">
                        <label for="email-login-admin">
                            E-MAIL ADMINISTRATIVO
                        </label>

                        <input
                            type="email"
                            id="email-login-admin"
                            name="email"
                            placeholder="admin@portoajuda.com.br"
                            autocomplete="username"
                            required
                        >
                    </div>

                    <div class="campo-login-admin">
                        <label for="senha-login-admin">
                            SENHA
                        </label>

                        <input
                            type="password"
                            id="senha-login-admin"
                            name="senha"
                            placeholder="Digite sua senha"
                            autocomplete="current-password"
                            required
                        >
                    </div>

                    <p
                        id="mensagem-login-admin"
                        class="mensagem-login-admin"
                        role="alert"
                        hidden
                    ></p>

                    <div class="acoes-login-admin">

                        <button
                            type="button"
                            id="voltar-aviso-admin"
                            class="botao-voltar-admin"
                        >
                            VOLTAR
                        </button>

                        <button
                            type="submit"
                            class="confirm-modal"
                            id="enviar-login-admin"
                        >
                            VERIFICAR CREDENCIAIS
                        </button>

                    </div>

                </form>

                <p class="status-acesso-admin">
                    AGUARDANDO AUTENTICAÇÃO... // SYS://SECURE
                </p>

            </div>

        </section>

        <section id="etapa-email-admin" hidden>
            <div class="modal-body">
                <div class="login-admin-identificacao">
                    <span>Autenticação necessária</span>
                    <span>LEVEL 02 // EMAIL VERIFICATION</span>
                </div>
                <h2 class="titulo-login-admin">CONFIRME SEU E-MAIL.</h2>
                <p class="descricao-login-admin">Se as credenciais forem elegíveis, um código será enviado ao e-mail cadastrado.</p>
                <form id="form-email-admin" autocomplete="off">
                    <div class="campo-login-admin">
                        <label for="codigo-email-admin">CÓDIGO DE VERIFICAÇÃO</label>
                        <input id="codigo-email-admin" name="emailCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required aria-describedby="mensagem-email-admin">
                    </div>
                    <p id="mensagem-email-admin" class="mensagem-login-admin" role="alert" aria-live="polite" hidden></p>
                    <div class="acoes-login-admin">
                        <button type="button" class="botao-voltar-admin" data-voltar-login>VOLTAR</button>
                        <button type="button" class="botao-voltar-admin" data-resend-channel="EMAIL">REENVIAR CODIGO</button>
                        <button type="submit" class="confirm-modal">VALIDAR CÓDIGO</button>
                    </div>
                </form>
            </div>
        </section>

        <section id="etapa-sms-admin" hidden>
            <div class="modal-body">
                <div class="login-admin-identificacao">
                    <span>Autenticação necessária</span>
                    <span>LEVEL 03 // SMS VERIFICATION</span>
                </div>
                <h2 class="titulo-login-admin">CONFIRME SEU TELEFONE.</h2>
                <p class="descricao-login-admin">Informe o codigo SMS enviado ao telefone cadastrado. Em localhost, use "OBTER CODIGO SMS DE TESTE" para ler o campo msg da resposta SMSMais do POST /send sandbox; o painel do provedor mostra historico/status.</p>
                <p class="descricao-login-admin">Informe o código enviado ao telefone cadastrado.</p>
                <form id="form-sms-admin" autocomplete="off">
                    <div class="campo-login-admin">
                        <label for="codigo-sms-admin">CÓDIGO DE VERIFICAÇÃO</label>
                        <input id="codigo-sms-admin" name="smsCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required aria-describedby="mensagem-sms-admin">
                    </div>
                    <p id="mensagem-sms-admin" class="mensagem-login-admin" role="alert" aria-live="polite" hidden></p>
                    <div class="acoes-login-admin">
                        <button type="button" class="botao-voltar-admin" data-reiniciar-login>REINICIAR</button>
                        <button type="button" class="botao-voltar-admin" data-resend-channel="SMS">REENVIAR CODIGO</button>
                        <button type="button" class="botao-voltar-admin" id="obter-codigo-sandbox-admin" hidden>OBTER CODIGO SMS DE TESTE</button>
                        <button type="submit" class="confirm-modal">VALIDAR CÓDIGO</button>
                    </div>
                </form>
            </div>
        </section>

        <section id="etapa-sessao-admin" hidden>
            <div class="modal-body">
                <div class="login-admin-identificacao">
                    <span>AUTHENTICATION COMPLETE</span>
                    <span>PRIVILEGED SESSION // ACTIVE</span>
                </div>
                <h2 class="titulo-login-admin">ACESSO ADMINISTRATIVO ATIVO.</h2>
                <p class="descricao-login-admin">Sua sessão administrativa está protegida e será encerrada por inatividade ou expiração.</p>
                <p id="mensagem-sessao-admin" class="mensagem-login-admin" role="alert" aria-live="polite" hidden></p>
                <div class="acoes-login-admin">
                    <button type="button" class="botao-voltar-admin" id="sair-admin">ENCERRAR SESSÃO</button>
                    <button type="button" class="confirm-modal" id="continuar-painel-admin">CONTINUAR</button>
                </div>
            </div>
        </section>

    </div>
</dialog>`;

            document.body.insertAdjacentHTML(
                'beforeend',
                conteudoModal
            );
        }

        /* =========================================
           2. REFERÊNCIAS DOS ELEMENTOS
           ========================================= */

        const modal = document.querySelector('#modal-acesso-admin');
        const canvas = document.querySelector('#chuva-matrix');
        const botaoFechar = document.querySelector('#fechar-acesso-admin');

        if (!modal || !canvas || !botaoFechar) {
            console.error('[Acesso Admin] Não foi possível inicializar o modal.');
            return;
        }

        const ctx = canvas.getContext('2d');

        if (!ctx) {
            console.error('[Acesso Admin] Não foi possível criar o contexto do canvas.');
            return;
        }

        /* =========================================
           3. FECHAMENTO DO MODAL
           ========================================= */

        botaoFechar.addEventListener('click', () => {
            modal.close();
        });


        const etapaAviso = document.querySelector('#etapa-aviso-admin');
        const etapaLogin = document.querySelector('#etapa-login-admin');
        const etapaEmail = document.querySelector('#etapa-email-admin');
        const etapaSms = document.querySelector('#etapa-sms-admin');
        const etapaSessao = document.querySelector('#etapa-sessao-admin');
        const botaoContinuarPainel = document.querySelector('#continuar-painel-admin');
        const botaoContinuar = document.querySelector('#continuar-acesso-admin');
        const botaoVoltar = document.querySelector('#voltar-aviso-admin');
        const formularioLogin = document.querySelector('#form-login-admin');
        const formularioEmail = document.querySelector('#form-email-admin');
        const formularioSms = document.querySelector('#form-sms-admin');
        const mensagemLogin = document.querySelector('#mensagem-login-admin');
        const mensagemEmail = document.querySelector('#mensagem-email-admin');
        const mensagemSms = document.querySelector('#mensagem-sms-admin');
        const mensagemSessao = document.querySelector('#mensagem-sessao-admin');
        const botaoCodigoSandbox = document.querySelector('#obter-codigo-sandbox-admin');
        let challengeId = null;
        let flowGeneration = 0;
        let activeRequestController = null;
        const etapas = [etapaAviso, etapaLogin, etapaEmail, etapaSms, etapaSessao];
        if (botaoCodigoSandbox && ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname)) {
            botaoCodigoSandbox.hidden = false;
            botaoCodigoSandbox.addEventListener('click', async () => {
                if (!challengeId) return;
                botaoCodigoSandbox.disabled = true;
                try {
                    const resposta = await fetch(`${API_URL}/admin/auth/sandbox/challenges/${encodeURIComponent(challengeId)}/code`, {
                        method: 'GET', credentials: 'include', cache: 'no-store', headers: { Accept: 'application/json' }
                    });
                    if (!resposta.ok) throw new Error('sandbox-code');
                    const dados = await resposta.json();
                    if (!/^\d{6}$/.test(dados.smsCode || '')) throw new Error('sandbox-code');
                    document.querySelector('#codigo-sms-admin').value = dados.smsCode;
                    mostrarMensagem(mensagemSms, 'Codigo de teste obtido da resposta de simulacao da SMSMais.');
                } catch {
                    mostrarMensagem(mensagemSms, 'Codigo ainda indisponivel. Aguarde a confirmacao do envio SMSMais e tente novamente.');
                } finally {
                    botaoCodigoSandbox.disabled = false;
                }
            });
        }

        function iniciarRequisicao() {
            if (activeRequestController) return null;
            activeRequestController = new AbortController();
            return activeRequestController;
        }

        function encerrarRequisicao(controller) {
            if (activeRequestController === controller) activeRequestController = null;
        }

        function mostrarEtapa(etapa, seletorFoco) {
            etapas.forEach(item => { item.hidden = item !== etapa; });
            if (seletorFoco) document.querySelector(seletorFoco)?.focus();
        }

        function mostrarMensagem(elemento, texto) {
            elemento.textContent = texto;
            elemento.hidden = false;
        }

        function limparMensagem(elemento) {
            elemento.textContent = '';
            elemento.hidden = true;
        }

        async function codigoDeErro(resposta) {
            try {
                const corpo = await resposta.json();
                return typeof corpo?.codigo === 'string' ? corpo.codigo : null;
            } catch {
                return null;
            }
        }

        function mostrarProximaEtapa(resposta, mensagem) {
            const next = adminAuthNextFactor(resposta);
            if (next === 'SMS') {
                mostrarEtapa(etapaSms, '#codigo-sms-admin');
                return;
            }
            if (next === 'DELIVERY' || resposta?.codigo === 'DELIVERY_PENDING') {
                mostrarMensagem(mensagem, 'A entrega está sendo confirmada. Aguarde alguns segundos e valide o mesmo código novamente.');
                return;
            }
            if (next === 'EMAIL') {
                mostrarEtapa(etapaEmail, '#codigo-email-admin');
                mostrarMensagem(mensagem, 'Confirme primeiro o código enviado ao e-mail cadastrado.');
                return;
            }
            mostrarMensagem(mensagem, 'A verificação ainda está pendente. Aguarde e tente novamente.');
        }

        async function obterCsrf(signal) {
            const resposta = await fetch(`${API_URL}/usuario/csrf`, {
                method: 'GET', credentials: 'include', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal
            });
            if (!resposta.ok) throw new Error('csrf');
            const csrf = await resposta.json();
            if (!csrf?.headerName || !csrf?.token) throw new Error('csrf');
            return csrf;
        }

        async function enviarPost(caminho, corpo, signal) {
            const csrf = await obterCsrf(signal);
            return fetch(`${API_URL}${caminho}`, {
                method: 'POST', credentials: 'include', cache: 'no-store',
                headers: {
                    Accept: 'application/json', 'Content-Type': 'application/json',
                    [csrf.headerName]: csrf.token
                },
                body: corpo === undefined ? undefined : JSON.stringify(corpo),
                signal
            });
        }

        async function verificarSessaoAtual(signal) {
            const resposta = await fetch(`${API_URL}/admin/auth/session`, {
                method: 'GET', credentials: 'include', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal
            });
            if (resposta.status === 401) return 'unauthenticated';
            if (resposta.status === 403) return 'forbidden';
            // Keep the existing 204 contract; expiry metadata is exposed in safe headers.
            if (resposta.status === 204) return 'authenticated';
            throw new Error('session');
        }

        async function mostrarSessaoSeValida(mensagemErro, generation, signal) {
            try {
                const resultado = await verificarSessaoAtual(signal);
                if (generation !== flowGeneration) return false;
                if (resultado === 'authenticated') {
                    mostrarEtapa(etapaSessao, '#continuar-painel-admin');
                    return true;
                }
                if (resultado === 'forbidden') {
                    mostrarEtapa(etapaLogin, '#email-login-admin');
                    mostrarMensagem(mensagemLogin, 'O acesso foi recusado por autorização. Nenhuma solicitação de logout foi enviada.');
                    return false;
                }
                challengeId = null;
                mostrarEtapa(etapaLogin, '#email-login-admin');
                mostrarMensagem(mensagemLogin, 'Nenhuma sessão administrativa válida foi confirmada. Inicie a autenticação novamente.');
            } catch {
                if (generation !== flowGeneration) return false;
                mostrarMensagem(mensagemErro, 'Não foi possível confirmar a sessão por falha de conexão. Verifique a rede e tente novamente.');
            }
            return false;
        }

        botaoFechar.addEventListener('click', () => modal.close());

        botaoContinuar.addEventListener('click', async () => {
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            botaoContinuar.disabled = true;
            try {
                const resultado = await verificarSessaoAtual(controller.signal);
                if (generation !== flowGeneration) return;
                if (resultado === 'authenticated') mostrarEtapa(etapaSessao, '#continuar-painel-admin');
                else if (resultado === 'forbidden') {
                    mostrarEtapa(etapaLogin, '#email-login-admin');
                    mostrarMensagem(mensagemLogin, 'O acesso foi recusado por autorização. Nenhuma solicitação de logout foi enviada.');
                } else {
                    mostrarEtapa(etapaLogin, '#email-login-admin');
                    mostrarMensagem(mensagemLogin, 'Nenhuma sessão administrativa válida foi confirmada. Autentique-se para continuar.');
                }
            } catch {
                if (generation !== flowGeneration) return;
                mostrarEtapa(etapaLogin, '#email-login-admin');
                mostrarMensagem(mensagemLogin, 'Não foi possível consultar uma sessão existente. Você pode tentar entrar novamente.');
            } finally {
                encerrarRequisicao(controller);
                botaoContinuar.disabled = false;
            }
        });

        botaoVoltar.addEventListener('click', () => {
            mostrarEtapa(etapaAviso);
            formularioLogin.reset();
            limparMensagem(mensagemLogin);
        });

        document.querySelectorAll('[data-resend-channel]').forEach(botao => {
            botao.addEventListener('click', async () => {
                if (!challengeId) return;
                const channel = botao.dataset.resendChannel;
                const mensagem = channel === 'EMAIL' ? mensagemEmail : mensagemSms;
                botao.disabled = true;
                try {
                    const resposta = await enviarPost(`/admin/auth/challenges/${encodeURIComponent(challengeId)}/resend`, { channel });
                    const dados = await resposta.json();
                    mostrarMensagem(mensagem, dados.mensagem || 'Solicitacao processada.');
                    if (resposta.status === 202) {
                        document.querySelector(channel === 'EMAIL' ? '#codigo-email-admin' : '#codigo-sms-admin').value = '';
                    }
                } catch {
                    mostrarMensagem(mensagem, 'Nao foi possivel solicitar o reenvio. Tente novamente.');
                } finally {
                    botao.disabled = false;
                }
            });
        });

        formularioLogin.addEventListener('submit', async event => {
            event.preventDefault();
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            limparMensagem(mensagemLogin);
            const botao = formularioLogin.querySelector('[type="submit"]');
            botao.disabled = true;
            botao.textContent = 'VERIFICANDO…';
            try {
                const resposta = await enviarPost('/admin/auth/challenges', {
                    email: document.querySelector('#email-login-admin').value.trim(),
                    senha: document.querySelector('#senha-login-admin').value
                }, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status !== 202) {
                    mostrarMensagem(mensagemLogin, adminAuthFailureMessage(resposta.status));
                    return;
                }
                const dados = await resposta.json();
                if (generation !== flowGeneration) return;
                if (!dados.challengeId) throw new Error('challenge');
                challengeId = dados.challengeId;
                document.querySelector('#senha-login-admin').value = '';
                mostrarEtapa(etapaEmail, '#codigo-email-admin');
            } catch {
                if (generation === flowGeneration) {
                    mostrarMensagem(mensagemLogin, 'Não foi possível obter resposta da API. Confira a disponibilidade do backend, a URL e a origem CORS; tente iniciar novamente.');
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
                botao.textContent = 'VERIFICAR CREDENCIAIS';
            }
        });

        formularioEmail.addEventListener('submit', async event => {
            event.preventDefault();
            const generation = flowGeneration;
            if (!challengeId) {
                mostrarMensagem(mensagemEmail, 'Esta tentativa expirou. Inicie novamente.');
                return;
            }
            const controller = iniciarRequisicao();
            if (!controller) return;
            limparMensagem(mensagemEmail);
            const botao = formularioEmail.querySelector('[type="submit"]');
            botao.disabled = true;
            botao.textContent = 'VALIDANDO…';
            try {
                const resposta = await enviarPost(`/admin/auth/challenges/${encodeURIComponent(challengeId)}/verify`, {
                    emailCode: document.querySelector('#codigo-email-admin').value.trim()
                }, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status === 202) mostrarProximaEtapa(await resposta.json(), mensagemEmail);
                else if (resposta.status === 204) {
                    challengeId = null;
                    await mostrarSessaoSeValida(mensagemEmail, generation, controller.signal);
                }
                else {
                    const codigo = await codigoDeErro(resposta);
                    mostrarMensagem(mensagemEmail, adminAuthFailureMessage(resposta.status, codigo));
                    if ([401, 409, 410, 429, 503].includes(resposta.status)) {
                        document.querySelector('#codigo-email-admin').value = '';
                    }
                }
            } catch {
                if (generation === flowGeneration) {
                    challengeId = null;
                    mostrarMensagem(mensagemEmail, 'A resposta do servidor não chegou. O código pode ter sido processado; reinicie a autenticação para obter novos códigos.');
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
                botao.textContent = 'VALIDAR CÓDIGO';
            }
        });

        formularioSms.addEventListener('submit', async event => {
            event.preventDefault();
            const generation = flowGeneration;
            if (!challengeId) {
                mostrarMensagem(mensagemSms, 'Esta tentativa expirou. Inicie novamente.');
                return;
            }
            const controller = iniciarRequisicao();
            if (!controller) return;
            limparMensagem(mensagemSms);
            const botao = formularioSms.querySelector('[type="submit"]');
            botao.disabled = true;
            botao.textContent = 'VALIDANDO…';
            try {
                const resposta = await enviarPost(`/admin/auth/challenges/${encodeURIComponent(challengeId)}/verify`, {
                    smsCode: document.querySelector('#codigo-sms-admin').value.trim()
                }, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status === 204) {
                    challengeId = null;
                    await mostrarSessaoSeValida(mensagemSms, generation, controller.signal);
                } else if (resposta.status === 202) {
                    mostrarProximaEtapa(await resposta.json(), mensagemSms);
                } else {
                    const codigo = await codigoDeErro(resposta);
                    mostrarMensagem(mensagemSms, adminAuthFailureMessage(resposta.status, codigo));
                    if ([401, 409, 410, 429, 503].includes(resposta.status)) document.querySelector('#codigo-sms-admin').value = '';
                }
            } catch {
                if (generation === flowGeneration) {
                    challengeId = null;
                    await mostrarSessaoSeValida(mensagemSms, generation, controller.signal);
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
                botao.textContent = 'VALIDAR CÓDIGO';
            }
        });

        document.querySelectorAll('[data-voltar-login], [data-reiniciar-login]').forEach(botao => {
            botao.addEventListener('click', () => {
                challengeId = null;
                formularioEmail.reset();
                formularioSms.reset();
                limparMensagem(mensagemEmail);
                limparMensagem(mensagemSms);
                mostrarEtapa(etapaLogin, '#email-login-admin');
            });
        });
        botaoContinuarPainel.addEventListener('click', async () => {
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            botaoContinuarPainel.disabled = true;
            limparMensagem(mensagemSessao);
            try {
                const resultado = await verificarSessaoAtual(controller.signal);
                if (generation !== flowGeneration) return;
                if (resultado === 'authenticated') {
                    window.location.assign(ROTAS.admin);
                    return;
                }
                if (resultado === 'forbidden') {
                    mostrarMensagem(mensagemSessao, 'O acesso ao painel foi recusado por autorização. Nenhuma solicitação de logout foi enviada.');
                    return;
                }
                challengeId = null;
                formularioLogin.reset();
                mostrarEtapa(etapaLogin, '#email-login-admin');
                mostrarMensagem(mensagemLogin, 'Sua sessão administrativa expirou. Autentique-se novamente.');
            } catch {
                if (generation === flowGeneration) {
                    mostrarMensagem(mensagemSessao, 'Não foi possível confirmar sua sessão. Tente novamente antes de continuar.');
                }
            } finally {
                encerrarRequisicao(controller);
                botaoContinuarPainel.disabled = false;
            }
        });
        document.querySelector('#sair-admin').addEventListener('click', async event => {
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            const botao = event.currentTarget;
            limparMensagem(mensagemSessao);
            botao.disabled = true;
            try {
                const resposta = await enviarPost('/admin/auth/logout', undefined, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status === 204) {
                    challengeId = null;
                    formularioLogin.reset();
                    window.dispatchEvent(new Event('porto-ajuda:admin-session-ended'));
                    mostrarEtapa(etapaLogin, '#email-login-admin');
                } else mostrarMensagem(mensagemSessao, adminAuthFailureMessage(resposta.status));
            } catch {
                if (generation === flowGeneration) {
                    mostrarMensagem(mensagemSessao, 'A resposta do logout não chegou. A sessão pode ter sido encerrada; reabra o modal para consultar o estado atual.');
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
            }
        });

        modal.addEventListener('close', () => {
            flowGeneration += 1;
            activeRequestController?.abort();
            activeRequestController = null;
            challengeId = null;
            formularioLogin.reset();
            formularioEmail.reset();
            formularioSms.reset();
            [mensagemLogin, mensagemEmail, mensagemSms, mensagemSessao].forEach(limparMensagem);
            mostrarEtapa(etapaAviso);
        });
        /* =========================================
           4. ANIMAÇÃO MATRIX
           ========================================= */

        const caracteres =
            '01アイウエオカキクケコサシスセソタチツテトナニヌネノ';

        const tamanhoFonte = 15;
        const velocidade = 0.65;

        const movimentoReduzido = window.matchMedia(
            '(prefers-reduced-motion: reduce)'
        );

        let largura = 0;
        let altura = 0;
        let proporcao = 1;

        let colunas = [];
        let animacao = null;

        function redimensionarCanvas() {

            proporcao = Math.min(
                window.devicePixelRatio || 1,
                2
            );

            largura = window.innerWidth;
            altura = window.innerHeight;

            canvas.width = Math.floor(largura * proporcao);
            canvas.height = Math.floor(altura * proporcao);

            canvas.style.width = `${largura}px`;
            canvas.style.height = `${altura}px`;

            ctx.setTransform(
                proporcao,
                0,
                0,
                proporcao,
                0,
                0
            );

            const quantidadeColunas = Math.ceil(
                largura / tamanhoFonte
            );

            colunas = Array.from(
                { length: quantidadeColunas },
                () => Math.random() * altura
            );

            ctx.clearRect(0, 0, largura, altura);
        }

        function desenharMatrix() {

            if (!modal.open || movimentoReduzido.matches) {
                pararChuvaMatrix();
                return;
            }

            // Cria o rastro dos caracteres.
            ctx.fillStyle = 'rgba(5, 12, 8, 0.12)';
            ctx.fillRect(0, 0, largura, altura);

            ctx.font = `${tamanhoFonte}px monospace`;
            ctx.fillStyle = '#008f52';

            colunas.forEach((y, indice) => {

                const caractere = caracteres.charAt(
                    Math.floor(Math.random() * caracteres.length)
                );

                const x = indice * tamanhoFonte;

                ctx.fillText(
                    caractere,
                    x,
                    y
                );

                // Reinicia a coluna ao ultrapassar a tela.
                if (
                    y > altura &&
                    Math.random() > 0.975
                ) {
                    colunas[indice] = -Math.random() * 200;
                } else {
                    colunas[indice] += tamanhoFonte * velocidade;
                }

            });

            animacao = requestAnimationFrame(desenharMatrix);
        }

        function iniciarChuvaMatrix() {

            if (
                animacao !== null ||
                movimentoReduzido.matches ||
                !modal.open
            ) {
                return;
            }

            redimensionarCanvas();

            animacao = requestAnimationFrame(desenharMatrix);
        }

        function pararChuvaMatrix() {

            if (animacao !== null) {
                cancelAnimationFrame(animacao);
                animacao = null;
            }

            ctx.clearRect(0, 0, largura, altura);
        }

        /* =========================================
           5. EVENTOS DO MODAL
           ========================================= */

        // Inicia a animação sempre que o modal for aberto.
        modal.addEventListener('close', () => {
            pararChuvaMatrix();
        });

        // Recalcula o canvas se a janela mudar de tamanho.
        window.addEventListener('resize', () => {

            if (modal.open) {
                redimensionarCanvas();
            }

        });

        // Interrompe ou reinicia conforme a preferência de movimento.
        movimentoReduzido.addEventListener('change', () => {

            if (movimentoReduzido.matches) {
                pararChuvaMatrix();
            } else if (modal.open) {
                iniciarChuvaMatrix();
            }

        });

        // Pausa a animação quando a aba fica oculta.
        document.addEventListener('visibilitychange', () => {

            if (document.hidden) {
                pararChuvaMatrix();
            } else if (modal.open) {
                iniciarChuvaMatrix();
            }

        });

        /* =========================================
           6. DETECÇÃO DE CAMPOS DE TEXTO
           ========================================= */

        function estaEmCampoDeTexto(elemento) {

            if (!(elemento instanceof Element)) {
                return false;
            }

            return Boolean(
                elemento.closest(`
                    input,
                    textarea,
                    select,
                    [contenteditable]:not([contenteditable="false"]),
                    [role="textbox"],
                    [role="searchbox"]
                `)
            ) || elemento.isContentEditable;

        }

        /* =========================================
           7. DETECÇÃO DO CÓDIGO IDDQD
           ========================================= */

        let sequenciaDigitada = '';

        document.addEventListener('keydown', (event) => {

            // Não acumula caracteres enquanto o modal estiver aberto.
            if (modal.open) {
                sequenciaDigitada = '';
                return;
            }

            // Ignora composição de texto e campos editáveis.
            if (
                event.isComposing ||
                estaEmCampoDeTexto(event.target)
            ) {
                sequenciaDigitada = '';
                return;
            }

            // Considera somente letras.
            if (!/^[a-z]$/i.test(event.key)) {
                sequenciaDigitada = '';
                return;
            }

            sequenciaDigitada += event.key.toUpperCase();

            // Mantém somente os últimos caracteres necessários.
            sequenciaDigitada = sequenciaDigitada.slice(
                -CODIGO_ACESSO.length
            );

            // Código identificado.
            if (sequenciaDigitada === CODIGO_ACESSO) {

                sequenciaDigitada = '';

                abrirModalAdmin();

            }

        });

        function abrirModalAdmin() {
            if (modal.open) return;
            modal.showModal();
            iniciarChuvaMatrix();
        }

        window.addEventListener('porto-ajuda:abrir-admin-login', abrirModalAdmin);

        /* =========================================
           8. LIMPEZA DA SEQUÊNCIA
           ========================================= */

        document.addEventListener('focusin', (event) => {

            if (estaEmCampoDeTexto(event.target)) {
                sequenciaDigitada = '';
            }

        });

        // Limpa a sequência quando a janela perde o foco.
        window.addEventListener('blur', () => {
            sequenciaDigitada = '';
        });

    }

    /* =========================================
       9. INICIALIZAÇÃO SEGURA DO DOM
       ========================================= */

    if (document.readyState === 'loading') {

        document.addEventListener(
            'DOMContentLoaded',
            inicializarAcessoAdmin,
            { once: true }
        );

    } else {

        inicializarAcessoAdmin();

    }

})();
