package com.portoajuda.aplicacao_osc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String inicio() {
        return "forward:/pages/Porto-Ajuda.html";
    }

    @GetMapping("/apoie")
    public String apoie() {
        return "forward:/pages/apoie.html";
    }

    @GetMapping("/contato")
    public String contato() {
        return "forward:/pages/contato.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/pages/login.html";
    }

    @GetMapping("/sobre")
    public String sobre() {
        return "forward:/pages/sobre.html";
    }

    @GetMapping("/iniciativas")
    public String iniciativas() {
        return "forward:/pages/iniciativas.html";
    }

    @GetMapping("/contribuicao")
    public String contribuicao() {
        return "forward:/pages/contribuicao.html";
    }

    @GetMapping("/doacao-ong")
    public String doacaoOng() {
        return "forward:/pages/doacao-ong.html";
    }

    @GetMapping("/perfil")
    public String perfil() {
        return "forward:/pages/perfil.html";
    }

    @GetMapping("/oscs")
    public String oscs() {
        return "forward:/pages/oscs-page.html";
    }

    @GetMapping("/proximidade")
    public String proximidade() {
        return "forward:/pages/proximity.html";
    }

    @GetMapping("/apoio")
    public String apoio() {
        return "forward:/pages/apoie.html";
    }

    @GetMapping("/ong-profile")
    public String ongProfile() {
        return "forward:/pages/ong-profile.html";
    }

        @GetMapping("/cadastro-ong")
    public String CadastroOng() {
        return "forward:/pages/cadastro-ong.html";
    }
}