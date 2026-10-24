package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Doacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoacaoRepository extends JpaRepository<Doacao, Integer> {

    boolean existsByUsuario_Id(Integer usuarioId);

    boolean existsByOsc_Id(Integer oscId);

    boolean existsByPost_Osc_Id(Integer oscId);
}
