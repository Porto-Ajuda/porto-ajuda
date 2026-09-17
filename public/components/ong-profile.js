const track = document.querySelector(".carrossel-track");
const imagens = document.querySelectorAll(".carrossel-track img");

let indice = 0;

function proximaImagem() {

    indice++;

    if (indice >= imagens.length) {
        indice = 0;
    }

    track.style.transform = `translateX(-${indice * 100}%)`;
}

setInterval(proximaImagem, 5000);