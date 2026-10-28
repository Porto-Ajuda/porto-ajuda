package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Post;
import com.portoajuda.aplicacao_osc.entity.Vaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VagaRepository extends JpaRepository<Vaga, Integer> {
    Optional<Vaga> findByPost(Post post);
}
