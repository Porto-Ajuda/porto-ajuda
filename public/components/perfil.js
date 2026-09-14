// =========================
// EDITAR DADOS DO PERFIL
// =========================

const btn = document.querySelector("#btn");
const inputs = document.querySelectorAll(".info-perfil input");

const infoPerfil = document.querySelector(".info-perfil");
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
const Seta = document.getElementsByClassName("btn-seta");
const Cards = document.getElementsByClassName("cards-container");
const DetalhesUsuario = document.getElementsByClassName("detalhes-usuario");
const DetalhesAcessibilidade = document.getElementsByClassName("detalhes-acessibilidade");
const Configuracoes = document.getElementsByClassName("detalhes-configuracoes");
Seta[0].addEventListener("click", () => {
    DetalhesUsuario[0].classList.toggle("active");
    DetalhesAcessibilidade[0].classList.remove("active");
    Configuracoes[0].classList.remove("active");
});

Seta[1].addEventListener("click", () => {
    DetalhesAcessibilidade[0].classList.toggle("active");
    DetalhesUsuario[0].classList.remove("active");
    Configuracoes[0].classList.remove("active");

});     
Seta[2].addEventListener("click", () => {
    DetalhesAcessibilidade[0].classList.remove("active");
    DetalhesUsuario[0].classList.remove("active");
    Configuracoes[0].classList.toggle("active");
});
const TogLB = document.getElementById('libra');
const TogAC = document.getElementById('contraste');

const circleLB = TogLB.querySelector('.toggle-circle');
const circleAC = TogAC.querySelector('.toggle-circle');
TogLB.addEventListener('click', () => {
    circleLB.classList.toggle('active');
    
});

TogAC.addEventListener('click', () => {
    circleAC.classList.toggle('active');
});
const usuarioLang = document.querySelector(".Ulang");

let NavLang = navigator.language.split("-")[0];

if (NavLang === "pt") {
    usuarioLang.value = "Português";
}
else if (NavLang === "en") {
    usuarioLang.value = "English";
}
else if (NavLang === "es") {
    usuarioLang.value = "Español";
}