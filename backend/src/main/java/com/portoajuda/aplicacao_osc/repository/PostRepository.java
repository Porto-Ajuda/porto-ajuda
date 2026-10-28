package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import com.portoajuda.aplicacao_osc.enums.TipoPost;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Integer> {
    Optional<Post> findById(Integer id);

    List<Post> findByTipo(TipoPost tipo);

    List<Post> findByOscId(Integer oscId);

    List<Post> findByOscCausas(CausaSocial causaSocial);
}
