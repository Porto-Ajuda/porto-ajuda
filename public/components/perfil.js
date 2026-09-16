// =========================
// EDITAR DADOS DO PERFIL
// =========================

const btn = document.querySelector(".btn");
const inputs = document.querySelectorAll(".campo-perfil input");

const infoPerfil = document.querySelector(".campo-perfil");
const rectangle = document.querySelector(".rectangle");

let editando = false;


btn.addEventListener("click", () => {

    // Inverte o estado
    editando = !editando;


    // Habilita ou desabilita os inputs
    inputs.forEach(input => {
        input.disabled = !editando;
    });


    // Mostra/esconde a senha
    infoPerfil.classList.toggle("editando", editando);


    // Aumenta/diminui o rectangle
    rectangle.classList.toggle("editando", editando);


    // Altera o texto do botão
    if (editando) {

        btn.textContent = "Salvar Dados";

    } else {

        btn.textContent = "Alterar Dados";

        setItemsToLocalStorage();
    }

});