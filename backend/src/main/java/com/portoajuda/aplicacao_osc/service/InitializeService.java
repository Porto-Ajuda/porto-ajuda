package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InitializeService {
    private final RoleRepository roleRepository;

    public void initializeRoles(){
        if (!roleRepository.existsByNome("ADMIN")){
            Role role = new Role();
            role.setNome("ADMIN");
            roleRepository.save(role);
        }
        if (!roleRepository.existsByNome("USUARIO")){
            Role role = new Role();
            role.setNome("USUARIO");
            roleRepository.save(role);
        }
        if (!roleRepository.existsByNome("OSC")){
            Role role = new Role();
            role.setNome("OSC");
            roleRepository.save(role);
        }
    }
}