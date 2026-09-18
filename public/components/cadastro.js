const formulario = document.getElementById("form-cadastro");

formulario.addEventListener("submit", function (event) {

    event.preventDefault();

    const nome = document.querySelector('input[placeholder="Nome"]').value;
    const cpf = document.getElementById("cpf").value;
    const email = document.querySelector('input[type="email"]').value;

    const genero = document.getElementById("genero").value;
    const telefone = document.getElementById("telefone").value;
    const nascimento = document.getElementById("nascimento").value;
    const cep = document.getElementById("cep").value;

    const senha = document.getElementById("senha-cadastro").value;

    const usuario = {
        cpf: cpf,
        cep: cep,
        nome: nome,
        nomeUsuario: "",
        dataNascimento: nascimento,
        email: email,
        genero: genero.toUpperCase(),
        telefone: telefone,
        senha: senha
    }
   
    postCadastro(usuario);
   
   event.target.submit();
});

function postCadastro(usuario) {

    fetch("https://porto-ajuda.up.railway.app/usuario/register", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(usuario)
    })
    .then(async response => {

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "Erro ao cadastrar usuário");
        }

        return data;
    })
    .then(data => {
        console.log("Cadastro realizado com sucesso!", data);
    })
    .catch(error => {
        console.error("Erro ao realizar cadastro:", error);
    });
}