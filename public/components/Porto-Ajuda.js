const cards = document.querySelectorAll(".card");

    const observer = new IntersectionObserver((entries) => {

      entries.forEach((entry) => {

        if (entry.isIntersecting) {

          entry.target.classList.add("show");

        }

      });

    }, {
      threshold: 0.2
    });

    cards.forEach((card) => {

      observer.observe(card);

    });


/*surgir*/
    const elements = document.querySelectorAll(".surgir");

const observer2 = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add("active");
    }
  });
}, {
  threshold: 0.15
});

elements.forEach((el) => observer2.observe(el));