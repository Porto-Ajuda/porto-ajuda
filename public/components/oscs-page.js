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


