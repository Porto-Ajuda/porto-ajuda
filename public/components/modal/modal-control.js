const openButtons = document.querySelectorAll('.open-modal');

openButtons.forEach(button => {
    button.addEventListener('click', () => {
        const modalId = button.getAttribute('data-modal');
        const modal = document.getElementById(modalId)

        modal.showModal();
    });
});

const closeButtons = document.querySelectorAll('.close-modal');

closeButtons.forEach(button => {
    button.addEventListener('click', () => {
        const modalId = button.getAttribute('data-modal');
        const modal = document.getElementById(modalId)

        modal.close();
    });
});


let termosConfirmados = false;
let lgpdConfirmada = false;


// CONFIRMAR OS MODAIS
document.addEventListener("click", (event) => {

    const button = event.target.closest(".confirm-modal");

    if (!button) return;

    const modalId = button.dataset.modal;

    if (modalId === "modal-termos") {
        termosConfirmados = true;
    }

    if (modalId === "modal-lgpd") {
        lgpdConfirmada = true;
    }

    const modal = document.getElementById(modalId);

    if (modal) {
        modal.close();
    }

});


// CHECKBOX
document.addEventListener("click", (event) => {

    const checkbox = event.target.closest("#check-termos");

    if (!checkbox) return;


    // Se ainda não confirmou os dois
    if (!termosConfirmados || !lgpdConfirmada) {

        event.preventDefault();

        checkbox.checked = false;

        mostrarAlerta(
            "Você precisa ler e confirmar os Termos de Uso e a Política de Privacidade antes de continuar."
        );

    }

});

function mostrarAlerta(tipo, titulo, mensagem) {

    const icones = {
        erro: "fa-circle-xmark",
        aviso: "fa-triangle-exclamation",
        sucesso: "fa-circle-check"
    };

    const alerta = document.createElement("div");
    alerta.className = `alerta-personalizado alerta-${tipo}`;

    alerta.innerHTML = `
        <div class="caixa-alerta">
            <div class="icone-alerta">
                <i class="fa-solid ${icones[tipo] || icones.aviso}"></i>
            </div>
            <h2></h2>
            <p></p>
            <button type="button" class="btn-alerta">Entendi</button>
        </div>
    `;

    // textContent evita interpretar HTML vindo do servidor
    alerta.querySelector("h2").textContent = titulo || "Atenção";
    alerta.querySelector("p").textContent = mensagem || "";

    document.body.appendChild(alerta);

    alerta.querySelector(".btn-alerta").addEventListener("click", () => {
        alerta.remove();
    });
}