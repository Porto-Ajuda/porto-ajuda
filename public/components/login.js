const frame = document.getElementById('frame');
const goCad = document.getElementById('goCad');
const goLogin = document.getElementById('goLogin');
const polygon = document.querySelector('.polygon');
goCad.addEventListener('click', () => {
  frame.classList.add('flipped');
    moveFrame('right');
    movePolygon();
});

goLogin.addEventListener('click', () => {
  frame.classList.remove('flipped');
  movePolygon();
  moveFrame('left');
});

function moveFrame(side) {
  if (side === 'left') {
    frame.style.left = '10%';
    frame.style.right = 'auto';
  } else {
    frame.style.right = '10%';
    frame.style.left = 'auto';
  }
}
