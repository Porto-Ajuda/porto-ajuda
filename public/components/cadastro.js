document
    .getElementById("form-cadastro")
    .addEventListener("submit", async function (event) {

        event.preventDefault();

        const usuario = {
            cpf: document.getElementById("cpf").value,

            cep: document.getElementById("cep").value.replace(/\D/g, ""),

            nome: document.querySelector("#form-cadastro input[placeholder='Nome']").value,

            nomeSocial: "",

            dataNascimento: converterDataParaISO(document.getElementById("nascimento").value),

            email: document.querySelector("#form-cadastro input[type='email']").value,

            genero: document.getElementById("genero").value,

            telefone: document.getElementById("telefone").value,

            senha:document.getElementById("senha-texto").value
        };


        await postCadastro(usuario);
    });

    function converterDataParaISO(data) {
    const [dia, mes, ano] = data.split("/");

    return `${ano}-${mes}-${dia}`;
    }

    function mostrarAlertaCadastro(mensagem) {

    const alerta = document.getElementById("alerta-cadastro");

    const texto = alerta.querySelector(".conteudo-cadastro p");

    texto.textContent =
        mensagem ||
        "Sua conta foi criada com sucesso.";

    alerta.hidden = false;

    document.body.style.overflow = "hidden";
}

document
    .getElementById("btn-ir-login")
    .addEventListener("click", function () {

        const alerta =
            document.getElementById("alerta-cadastro");

        alerta.hidden = true;

        document.body.style.overflow = "";

        /*
         * Volta para o painel de login
         */

        const frame =
            document.getElementById("frame");

        frame.classList.remove("flipped");

        /*
         * Limpa o formulário de cadastro
         */

        const formulario =
            document.getElementById("form-cadastro");

        if (formulario) {
            formulario.reset();
        }

        /*
         * Coloca o foco no CPF do login,
         * se existir.
         */

        const cpfLogin =
            document.querySelector(
                ".panel-login input[placeholder='CPF']"
            );

        if (cpfLogin) {
            setTimeout(() => {
                cpfLogin.focus();
            }, 500);
        }

    });



async function postCadastro(usuario) {

    try {

        const response = await fetch(
            "https://porto-ajuda.up.railway.app/usuario/register",
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(usuario)
            }
        );


        let data = {};

        const contentType =
            response.headers.get("content-type");

        if (
            contentType &&
            contentType.includes("application/json")
        ) {
            data = await response.json();
        }


        /*
         * ==========================
         * CADASTRO REALIZADO
         * ==========================
         */

        if (response.ok) {

            mostrarAlertaCadastro(
                data.message ||
                "Sua conta foi criada com sucesso."
            );

            return data;
        }


        /*
         * ==========================
         * ERRO 400
         * ==========================
         */

        if (response.status === 400) {

            mostrarAlerta(
                "erro",
                "Dados inválidos",
                data.message ||
                "Verifique os dados informados."
            );

            return null;
        }


        /*
         * ==========================
         * ERRO 401
         * ==========================
         */

        if (response.status === 401) {

            mostrarAlerta(
                "erro",
                "Não autorizado",
                data.message ||
                "Você não possui autorização para realizar esta operação."
            );

            return null;
        }


        /*
         * ==========================
         * ERRO 409
         * ==========================
         */

        if (response.status === 409) {

            mostrarAlerta(
                "aviso",
                "Usuário já cadastrado",
                data.message ||
                "Já existe um usuário cadastrado com esses dados."
            );

            return null;
        }


        /*
         * ==========================
         * ERRO 500+
         * ==========================
         */

        if (response.status >= 500) {

            mostrarAlerta(
                "erro",
                "Erro no servidor",
                "Ocorreu um problema no servidor. Tente novamente mais tarde."
            );

            return null;
        }


        /*
         * ==========================
         * OUTROS ERROS
         * ==========================
         */

        mostrarAlerta(
            "erro",
            "Não foi possível cadastrar",
            data.message ||
            `O servidor retornou o código ${response.status}.`
        );

        return null;


    } catch (error) {

        console.error("Erro ao realizar cadastro:", error);

        mostrarAlerta(
            "erro",
            "Erro de conexão",
            "Não foi possível conectar ao servidor."
        );

        return null;
    }
}

