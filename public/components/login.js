const frame = document.getElementById('frame');
const goCad = document.getElementById('goCad');
const goLogin = document.getElementById('goLogin');
const polygon = document.querySelector('.polygon');
goCad.addEventListener('click', () => {
  frame.classList.add('flipped');
  polygonSide();
    moveFrame('right');
});

goLogin.addEventListener('click', () => {
  frame.classList.remove('flipped');
  polygonSide();
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
function polygonSide() {
  if (frame.classList.contains('flipped')) {
    polygon.style.right = 'auto';
    polygon.style.left = '0';
    polygon.style.transform = 'scaleX(-1)';
  } else {
    polygon.style.left = 'auto';
    polygon.style.right = '0';
    polygon.style.transform = 'scaleX(1)';
  }
}