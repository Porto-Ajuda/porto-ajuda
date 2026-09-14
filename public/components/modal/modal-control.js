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

const confirmButtons = document.querySelectorAll(".confirm-modal");

confirmButtons.forEach(button => {

    button.addEventListener("click", () => {

        const modalId = button.dataset.modal;

        if (modalId === "modal-termos") {
            termosConfirmados = true;
        }

        if (modalId === "modal-lgpd") {
            lgpdConfirmada = true;
        }

        const modal = document.getElementById(modalId);
        modal.close();

        liberarCheckbox();

    });

});

function liberarCheckbox() {

    const checkbox = document.querySelector("#check-termos");

    if (termosConfirmados && lgpdConfirmada) {
        checkbox.disabled = false;
    }

}