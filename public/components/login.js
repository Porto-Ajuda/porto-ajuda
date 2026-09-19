/* =====================================================
 * login.js
 * Formulário de LOGIN, animação de virar o cartão e
 * botão de mostrar/ocultar senha.
 * Máscaras, etapas e validações do cadastro ficam no
 * cadastro.js.
 * ===================================================== */
(function () {
    "use strict";

    const API_URL = "https://porto-ajuda.up.railway.app";

    const frame = document.getElementById("frame");
    const goCad = document.getElementById("goCad");
    const goLogin = document.getElementById("goLogin");
    const formLogin = document.getElementById("form-login");

    // ---------- ANIMAÇÃO LOGIN <-> CADASTRO ----------
    function moveFrame(side) {
        if (side === "left") {
            frame.style.left = "15%";
            frame.style.right = "auto";
        } else {
            frame.style.right = "50%";
            frame.style.left = "auto";
        }
    }

    goCad.addEventListener("click", () => {
        frame.classList.add("flipped");
        moveFrame("right");
    });

    goLogin.addEventListener("click", () => {
        frame.classList.remove("flipped");
        moveFrame("left");
    });

    // ---------- MOSTRAR / OCULTAR SENHA ----------
    document.querySelectorAll(".mostrar-senha").forEach((botao) => {

        botao.addEventListener("click", () => {

            const campoSenha = botao.previousElementSibling;

            if (campoSenha.type === "password") {
                campoSenha.type = "text";
                botao.textContent = "🙈";
            } else {
                campoSenha.type = "password";
                botao.textContent = "👁️";
            }
        });
    });

    // ---------- LOGIN ----------
    formLogin.addEventListener("submit", async function (event) {

        event.preventDefault();

        const usuarioLogin = {
            email: document.getElementById("email-login").value.trim(),
            senha: document.getElementById("senha-login").value
        };

        const botao = formLogin.querySelector("button[type='submit']");
        botao.disabled = true;

        try {
            await postLogin(usuarioLogin);
        } finally {
            botao.disabled = false;
        }
    });

    async function postLogin(usuarioLogin) {

        try {

            const response = await fetch(`${API_URL}/usuario/login`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(usuarioLogin)
            });

            const texto = await response.text();
            let data = {};

            try {
                data = texto ? JSON.parse(texto) : {};
            } catch (e) {
                data = {};
            }

            console.log("Resposta do login:", response.status);

            if (response.ok) {
                localStorage.setItem("dadosUsuario", JSON.stringify(data));
                window.location.href = "../pages/Porto-Ajuda.html";
                return;
            }

            if (response.status === 400) {
                mostrarAlerta("erro", "Dados inválidos", data.message || "Verifique os dados informados.");
                return;
            }

            if (response.status === 401 || response.status === 403) {
                mostrarAlerta("erro", "Login inválido", data.message || "E-mail ou senha incorretos.");
                return;
            }

            if (response.status >= 500) {
                mostrarAlerta("erro", "Erro no servidor", "Ocorreu um problema no servidor. Tente novamente mais tarde.");
                return;
            }

            mostrarAlerta(
                "erro",
                "Não foi possível efetuar o login",
                data.message || `O servidor retornou o código ${response.status}.`
            );

        } catch (error) {

            console.error("Erro ao realizar login:", error);

            mostrarAlerta(
                "erro",
                "Erro de conexão",
                "Não foi possível conectar ao servidor."
            );
        }
    }

})();


const frame = document.getElementById('frame');
const goCad = document.getElementById('goCad');
const goLogin = document.getElementById('goLogin');
const polygon = document.querySelector('.shape');
const cpf = document.getElementById('cpf');
goCad.addEventListener('click', () => {
    frame.classList.add('flipped');
    moveFrame('right');

});

goLogin.addEventListener('click', () => {
    frame.classList.remove('flipped');
    moveFrame('left');
});

function moveFrame(side) {
    if (side === 'left') {
        frame.style.left = '15%';
        frame.style.right = 'auto';
    } else {
        frame.style.right = '50%';
        frame.style.left = 'auto';
    }
}

cpf.addEventListener('input', () => {

    const cpfValue = cpf.value.replace(/\D/g, '');

    cpf.value = cpfValue.replace(
        /(\d{3})(\d{3})(\d{3})(\d{2})/,
        '$1.$2.$3-$4'
    );

    cpf.setCustomValidity('');
});

const telefone = document.getElementById('telefone');

telefone.addEventListener('input', () => {

    // Pega somente os números
    let valor = telefone.value.replace(/\D/g, '');

    // Limita a 11 números
    valor = valor.substring(0, 11);

    // Aplica a máscara
    if (valor.length <= 10) {

        valor = valor.replace(
            /^(\d{2})(\d{4})(\d{0,4})$/,
            '($1) $2-$3'
        );

    } else {

        valor = valor.replace(
            /^(\d{2})(\d{5})(\d{0,4})$/,
            '($1) $2-$3'
        );
    }

    telefone.value = valor;

    // Limpa erro anterior
    telefone.setCustomValidity('');
});

const cep = document.getElementById('cep');

cep.addEventListener('input', () => {

    let valor = cep.value.replace(/\D/g, '');

    // Limita a 8 números
    valor = valor.substring(0, 8);

    // Coloca o hífen
    if (valor.length > 5) {
        valor = valor.replace(
            /^(\d{5})(\d{0,3})$/,
            '$1-$2'
        );
    }

    cep.value = valor;

    // Remove eventual erro anterior
    cep.setCustomValidity('');
});

function validarCPF(cpf) {

    cpf = cpf.replace(/\D/g, '');

    // CPF precisa ter 11 dígitos
    if (cpf.length !== 11) {
        return false;
    }

    // Bloqueia CPFs com todos os números iguais
    if (/^(\d)\1{10}$/.test(cpf)) {
        return false;
    }

    // Primeiro dígito verificador
    let soma = 0;

    for (let i = 0; i < 9; i++) {
        soma += Number(cpf[i]) * (10 - i);
    }

    let resto = (soma * 10) % 11;

    if (resto === 10) {
        resto = 0;
    }

    if (resto !== Number(cpf[9])) {
        return false;
    }

    // Segundo dígito verificador
    soma = 0;

    for (let i = 0; i < 10; i++) {
        soma += Number(cpf[i]) * (11 - i);
    }

    resto = (soma * 10) % 11;

    if (resto === 10) {
        resto = 0;
    }

    if (resto !== Number(cpf[10])) {
        return false;
    }

    return true;
}

function validarIdade() {
    const nascimento = document.getElementById("nascimento").value;

    if (!nascimento) {
        return false;
    }

    const dataNascimento = new Date(nascimento);
    const hoje = new Date();

    let idade = hoje.getFullYear() - dataNascimento.getFullYear();

    const mes = hoje.getMonth() - dataNascimento.getMonth();

    if (
        mes < 0 ||
        (mes === 0 && hoje.getDate() < dataNascimento.getDate())
    ) {
        idade--;
    }

    return idade >= 16;
}

//** transição do cadastro **//

const nextBtn = document.getElementById('nextBtn');
const cad = document.getElementById('cad');
const backBtn = document.getElementById('backBtn');

const rows = [
    document.querySelector('.row2'),
    document.querySelector('.row3'),
    document.querySelector('.row4')
];

let etapa = 0;

// Começa mostrando somente a primeira etapa
rows.forEach((row, index) => {
    row.style.display = index === 0 ? 'grid' : 'none';
});

cad.style.display = 'none';
backBtn.style.display = 'none';


// AVANÇAR
nextBtn.addEventListener('click', () => {

    // Pega os campos da etapa atual
    const campos = rows[etapa].querySelectorAll('input');

    // Verifica se todos estão preenchidos
    for (const campo of campos) {

        if (!campo.checkValidity()) {
            campo.reportValidity();
            campo.focus();
            return;
        }
    }

    if (etapa === 0) {

        if (!validarCPF(cpf.value)) {

            cpf.setCustomValidity('Digite um CPF válido.');
            cpf.reportValidity();
            cpf.focus();

            return;
        }

        cpf.setCustomValidity('');
    }

    // ==========================================
    // VALIDA GÊNERO
    // ==========================================

    if (etapa === 1 && generoEscolhido === "") {
        
        dropdownGenero.classList.add("erro");
        erroGenero.style.display = "block";
        return;
    }

    if (etapa === 1) {
        if (!document.getElementById("genero").value) {
    document.getElementById("erro-genero").style.display = "block";
    return;
}
        const telefoneNumeros = telefone.value.replace(/\D/g, '');

        if (
            telefoneNumeros.length !== 10 &&
            telefoneNumeros.length !== 11
        ) {

            telefone.setCustomValidity('Digite um telefone válido.');
            telefone.reportValidity();
            telefone.focus();

            return;
        }

        telefone.setCustomValidity('');

        const cepNumeros = cep.value.replace(/\D/g, '');

        if (cepNumeros.length !== 8) {
            cep.setCustomValidity('Digite um CEP válido.');
            cep.reportValidity();
            cep.focus();
            return;
        }

        cep.setCustomValidity('');
    }

    // Esconde a etapa atual
    rows[etapa].style.display = 'none';

    // Avança
    etapa++;

    // Mostra a próxima etapa
    rows[etapa].style.display = 'grid';

    // Mostra o botão Voltar
    backBtn.style.display = 'block';

    // Se chegou na última etapa
    if (etapa === rows.length - 1) {
        nextBtn.style.display = 'none';
        cad.style.display = 'block';
    }
});


// VOLTAR
backBtn.addEventListener('click', () => {

    // Esconde a etapa atual
    rows[etapa].style.display = 'none';

    // Volta uma etapa
    etapa--;

    // Mostra a etapa anterior
    rows[etapa].style.display = 'grid';

    // Se voltou para a primeira etapa
    if (etapa === 0) {
        backBtn.style.display = 'none';
    }

    // Se saiu da última etapa
    if (etapa < rows.length - 1) {
        nextBtn.style.display = 'block';
        cad.style.display = 'none';
    }
});

const formCadastro = document.querySelector('.panel-cadastro .formulario');
const termos = document.getElementById('check-termos');

formCadastro.addEventListener('submit', (e) => {

    if (!termos.checked) {
        e.preventDefault();
        termos.reportValidity();
    }

});

const senha = document.querySelector("#senha");
const botoesSenha = document.querySelectorAll('.mostrar-senha');

botoesSenha.forEach(botao => {

    botao.addEventListener('click', () => {

        const campoSenha = botao.previousElementSibling;

        if (campoSenha.type === 'password') {

            campoSenha.type = 'text';
            botao.textContent = '🙈';

        } else {

            campoSenha.type = 'password';
            botao.textContent = '👁️';

        }

    });

});

const senhaCadastro = document.getElementById('senha-texto');
const confirmarSenha = document.getElementById('confirmar-senha');

const requisitosSenha = document.querySelector('.requisitos-senha');

senhaCadastro.addEventListener('focus', () => {
    const posicao = senhaCadastro.getBoundingClientRect();

    requisitosSenha.style.display = 'flex';
    requisitosSenha.style.left = `${posicao.right + 30}px`;
    requisitosSenha.style.top = `${posicao.top}px`;
});

senhaCadastro.addEventListener('blur', () => {
    requisitosSenha.style.display = 'none';
});

const nivelSenha = document.getElementById('nivel-senha');
const textoSeguranca = document.getElementById('texto-seguranca');

const reqTamanho = document.getElementById('req-tamanho');
const reqMaiuscula = document.getElementById('req-maiuscula');
const reqMinuscula = document.getElementById('req-minuscula');
const reqNumero = document.getElementById('req-numero');
const reqEspecial = document.getElementById('req-especial');


senhaCadastro.addEventListener('input', () => {

    const senha = senhaCadastro.value;

    const tamanho = senha.length >= 8;
    const maiuscula = /[A-Z]/.test(senha);
    const minuscula = /[a-z]/.test(senha);
    const numero = /[0-9]/.test(senha);
    const especial = /[^A-Za-z0-9]/.test(senha);


    // =========================
    // MOSTRA SOMENTE O QUE FALTA
    // =========================

    reqTamanho.style.display = tamanho ? 'none' : 'block';
    reqMaiuscula.style.display = maiuscula ? 'none' : 'block';
    reqMinuscula.style.display = minuscula ? 'none' : 'block';
    reqNumero.style.display = numero ? 'none' : 'block';
    reqEspecial.style.display = especial ? 'none' : 'block';

    // =========================
    // CALCULA A FORÇA
    // =========================

    let pontos = 0;

    if (tamanho) pontos++;
    if (maiuscula) pontos++;
    if (minuscula) pontos++;
    if (numero) pontos++;
    if (especial) pontos++;

    if (pontos === 5) {
        requisitosSenha.style.display = 'none';
    } else if (document.activeElement === senhaCadastro) {
        requisitosSenha.style.display = 'flex';
    }

    // =========================
    // ATUALIZA A BARRA
    // =========================

    nivelSenha.style.width = `${pontos * 20}%`;


    if (senha.length === 0) {

        textoSeguranca.textContent = 'Digite uma senha';

    } else if (pontos <= 2) {

        textoSeguranca.textContent = 'Senha fraca';

    } else if (pontos <= 4) {

        textoSeguranca.textContent = 'Senha média';

    } else {

        textoSeguranca.textContent = 'Senha forte';

    }

    confirmarSenha.setCustomValidity('');
});

confirmarSenha.addEventListener('input', () => {

    if (confirmarSenha.value !== senhaCadastro.value) {

        confirmarSenha.setCustomValidity(
            'As senhas não são iguais.'
        );

    } else {

        confirmarSenha.setCustomValidity('');

    }

});

cad.addEventListener('click', (e) => {

    const senha = senhaCadastro.value;

    const tamanho = senha.length >= 8;
    const maiuscula = /[A-Z]/.test(senha);
    const minuscula = /[a-z]/.test(senha);
    const numero = /[0-9]/.test(senha);
    const especial = /[^A-Za-z0-9]/.test(senha);

    if (!tamanho || !maiuscula || !minuscula || !numero || !especial) {

        e.preventDefault();

        senhaCadastro.setCustomValidity(
            'A senha não atende aos requisitos de segurança.'
        );

        senhaCadastro.reportValidity();
        senhaCadastro.focus();

        return;
    }

    senhaCadastro.setCustomValidity('');


    // Verifica se as senhas são iguais
    if (senhaCadastro.value !== confirmarSenha.value) {

        e.preventDefault();

        confirmarSenha.setCustomValidity(
            'As senhas não são iguais.'
        );

        confirmarSenha.reportValidity();
        confirmarSenha.focus();

        return;
    }

    confirmarSenha.setCustomValidity('');

});

const rect = senhaCadastro.getBoundingClientRect();

requisitosSenha.style.left = `${rect.right + 15}px`;
requisitosSenha.style.top = `${rect.top}px`;
