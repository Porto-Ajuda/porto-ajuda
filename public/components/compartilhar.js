const compartilhar = document.getElementById("compartilhar");
const textoCompartilhar = compartilhar.querySelector("span");

compartilhar.addEventListener("click", function (event) {
    event.preventDefault();

    navigator.clipboard.writeText(window.location.href);

    textoCompartilhar.textContent = "Link copiado!";
    textoCompartilhar.style.color = "var(--gold-soft)";

    setTimeout(() => {
        textoCompartilhar.textContent = "Compartilhar";
        textoCompartilhar.style.color = "";
    }, 2000);
});