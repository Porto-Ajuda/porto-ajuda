const frame = document.getElementById('frame');

const goCad = document.getElementById('goCad');
const goLogin = document.getElementById('goLogin');

goCad.addEventListener('click', () => {
  frame.classList.add('flipped');
});

goLogin.addEventListener('click', () => {
  frame.classList.remove('flipped');
});