const frame = document.getElementById('frame');
const goCad = document.getElementById('goCad');
const goLogin = document.getElementById('goLogin');
const polygon = document.querySelector('.polygon');
const moon = document.getElementById('moonT');
goCad.addEventListener('click', () => {
  frame.classList.add('flipped');
   movePolygon();
    moveFrame('right');
    
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
function movePolygon() {
  if (frame.classList.contains('flipped')) {
    polygon.style.transform = 'translateX(-250%) scaleX(1)';
     moon.style.transform = 'translateX(-410%) scaleX(1)';
    polygon.addEventListener('transitionend', function handler(e) {
      if (e.propertyName !== 'transform') return;

      polygon.removeEventListener('transitionend', handler);
      moon.removeEventListener('transitionend', handler);
      polygon.style.transform = 'translateX(-150%) scaleX(-1)';
       moon.style.transform = 'translateX(-155%) scaleX(1)';
    });
  }
}