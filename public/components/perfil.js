
// Script para editar os dados do perfil  
const btn = document.querySelector("#btn");
const inputs = document.querySelectorAll(".info-perfil input");
let editando = false;
btn.addEventListener("click", () => {
    editando = !editando;
    inputs.forEach(input => {
        input.disabled = !editando;
    });

    if (editando) {
        btn.textContent = "Salvar Dados";
    } else {
        btn.textContent = "Alterar Dados";

       setItemsToLocalStorage();
    }
});
