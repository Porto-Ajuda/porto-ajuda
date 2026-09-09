
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
const Seta = document.getElementsByClassName("btn-seta");
const Cards = document.getElementsByClassName("cards-container");
const DetalhesUsuario = document.getElementsByClassName("detalhes-usuario");
const DetalhesAcessibilidade = document.getElementsByClassName("detalhes-acessibilidade");

Seta[0].addEventListener("click", () => {
    DetalhesUsuario[0].classList.toggle("active");
    DetalhesAcessibilidade[0].classList.remove("active");
});

Seta[1].addEventListener("click", () => {
    DetalhesAcessibilidade[0].classList.toggle("active");
    DetalhesUsuario[0].classList.remove("active");
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