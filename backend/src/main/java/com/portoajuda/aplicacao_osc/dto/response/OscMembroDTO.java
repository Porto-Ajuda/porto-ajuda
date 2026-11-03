package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.enums.CargoOsc;

public record OscMembroDTO(
        Integer usuarioId,
        String nome,
        String email,
        CargoOsc cargo) {

    public static OscMembroDTO de(OscMembros membro) {
        var usuario = membro.getUsuario();
        String nome = usuario.getNomeSocial() == null || usuario.getNomeSocial().isBlank()
                ? usuario.getNome()
                : usuario.getNomeSocial();
        return new OscMembroDTO(
                usuario.getId(),
                nome,
                usuario.getEmail().valor(),
                membro.getCargoOsc());
    }
}
