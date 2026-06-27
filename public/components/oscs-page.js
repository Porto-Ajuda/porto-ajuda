/*SURGIR*/ 
document.addEventListener("DOMContentLoaded", () => {

            const elementosSurgir = document.querySelectorAll(".surgir");

            const observador = new IntersectionObserver((entradas) => {
                entradas.forEach(entrada => {
                    if (entrada.isIntersecting) {
                        entrada.target.classList.add("active");
                    }
                });
            }, { threshold: 0.15 });

            elementosSurgir.forEach(el => observador.observe(el));

        });

/*==============================*/
/*===== (chamando infos)POST-CARDS TEMPLATE=====*/
/*============================*/

const container = document.getElementById("feed-posts");
const template = document.getElementById("post-template");

console.log("container:", container);
console.log("template:", template);
console.log("listarPostsTipo:", typeof listarPostsTipo);

document.addEventListener("bancoPronto", () => {

    if (!container || !template) {
        console.error("Container ou template não encontrado.");
        return;
    }

    listarPostsTipo("osc", (posts) => {

        console.log("posts recebidos:", posts);

        posts.forEach(post => {

            const clone = template.content.cloneNode(true);

            clone.querySelector(".post-title").textContent =
                post.titulo;

            clone.querySelector(".post-description").textContent =
                post.conteudo;

            clone.querySelector(".post-ong").textContent =
                post.nomeOsc;

            if (post.imagem) {
                clone.querySelector(".post-image").src =
                    URL.createObjectURL(post.imagem);
            }

            container.appendChild(clone);

        });

    });

});