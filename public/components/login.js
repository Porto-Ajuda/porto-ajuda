const frame = document.getElementById('frame');
const goCad = document.getElementById('goCad');
const goLogin = document.getElementById('goLogin');
const polygon = document.querySelector('.shape');
const CPF = document.getElementById('cpf');
goCad.addEventListener('click', () => {
  frame.classList.add('flipped');
    moveFrame('right');
    
});

goLogin.addEventListener('click', () => {
  frame.classList.remove('flipped');
  moveFrame('left');
});

function moveFrame(side) {
  if (side === 'left') {
    frame.style.left = '15%';
    frame.style.right = 'auto';
  } else {
    frame.style.right = '50%';
    frame.style.left = 'auto';
  }
}
cpf.addEventListener('input', () => {
  const cpfValue = cpf.value.replace(/\D/g, '');
  cpf.value = cpfValue.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
});
//** transição do cadastro **//
const nextBtn = document.getElementById('nextBtn');

const rows = [
    document.querySelector('.row2'),
    document.querySelector('.row3'),
    document.querySelector('.row4')
];

let etapa = 0;

rows.forEach((row, index) => {
    row.style.display = index === 0 ? 'grid' : 'none';
});


nextBtn.addEventListener('click', () => {
    rows[etapa].style.display = 'none';
    etapa++;

    if (etapa < rows.length) {

        rows[etapa].style.display = 'grid';

        nextBtn.style.display =
            etapa === rows.length - 1 ? 'none' : 'block';

        cad.style.display =
            etapa === rows.length - 1 ? 'block' : 'none';
    }

});const form = document.querySelector('.formulario');
const termos = document.getElementById('termos');

form.addEventListener('submit', (e) => {
    if (!termos.checked) {
        e.preventDefault(); 
       
    }
});