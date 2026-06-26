const header = document.querySelector(".navbar");
const cabecalho =
  `        <a href="Porto-Ajuda.html">
    <div class="logo">
    
      <div class="logo-circle">
        <img class="logo-circle-img" src="../assets/logo-night.png">
      </div>

      <span >Porto Ajuda</span>

    </div>
    </a>

    <nav>

      <ul class="nav-links">

      <li><a href="Porto-Ajuda.html">Home</a></li>
      <li><a href="sobre.html">Sobre</a></li>
      <li><a href="#">FAQ</a></li>
      <li><a href="#">Ajude Nosso Site</a></li>
      <li><a href="proximity.html">OSC's Próximas</a></li>
        
        
        

      </ul>

    </nav>

    <div class="profile-icon">
      <a href="login.html"><i class="fa-regular fa-user"></i></a>
    </div>
  `

header.innerHTML = cabecalho;

const footer = document.querySelector("#footer");
const rodape =
  `
    <div class="footer-logo">
      <div class="logo-footer">
        <img class="img" src="../assets/logo-night.png">
        <div class="logo-name">Porto Ajuda</div>
      </div>
    </div>

    <div class="footer-links">

      <a href="#">Política De Privacidade</a>
      <a href="#">Termos De Uso</a>
      <a href="contato.html">Contato</a>

    </div>
  `

footer.innerHTML = rodape;