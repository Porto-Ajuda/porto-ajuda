package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.PostCausaId;
import com.portoajuda.aplicacao_osc.entity.PostCausas;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCausasRepository extends JpaRepository<PostCausas, PostCausaId> {
}
