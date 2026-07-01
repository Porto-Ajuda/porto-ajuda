package com.portoajuda.aplicacao_osc.entity;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Email {
    @Getter
    private String email;

    public Email(String email){
        validateEmail(email);
        this.email = email;
    };

    private void validateEmail(String email){
        if(email == null){
            throw new NullPointerException("Preenche um email");
        }
        String expression = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"+"[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(email);
        if(!matcher.matches()){
            throw new IllegalArgumentException("Formato Inválido");
        }
    }
}
