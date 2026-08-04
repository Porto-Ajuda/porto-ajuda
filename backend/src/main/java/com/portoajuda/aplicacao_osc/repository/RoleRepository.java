package com.portoajuda.aplicacao_osc.repository;

import com.portoajuda.aplicacao_osc.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    boolean existsByNome(String nome);

    Optional<Role> findByNome(String usuario);

}
