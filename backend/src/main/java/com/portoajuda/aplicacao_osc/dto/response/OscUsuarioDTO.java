package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.enums.CargoOsc;

/** Dados mínimos de uma OSC vinculada ao usuário autenticado. */
public record OscUsuarioDTO(
                Integer id,
                String nome,
                String logo,
                CargoOsc cargo) {
}