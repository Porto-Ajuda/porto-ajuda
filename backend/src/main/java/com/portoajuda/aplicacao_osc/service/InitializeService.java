package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.Permissao;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.repository.PermissaoRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InitializeService {
    private final RoleRepository roleRepository;
    private final PermissaoRepository permissaoRepository;

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

    public void initializePermissoes(){
        if (!permissaoRepository.existsByNome("MEMBRO_CREATE")){
            Permissao permissao = new Permissao();
            permissao.setNome("MEMBRO_CREATE");
            permissaoRepository.save(permissao);
        }
        if (!permissaoRepository.existsByNome("MEMBRO_UPDATE")){
            Permissao permissao = new Permissao();
            permissao.setNome("MEMBRO_UPDATE");
            permissaoRepository.save(permissao);
        }
        if (!permissaoRepository.existsByNome("MEMBRO_DELETE")){
            Permissao permissao = new Permissao();
            permissao.setNome("MEMBRO_DELETE");
            permissaoRepository.save(permissao);
        }
        if (!permissaoRepository.existsByNome("MEMBRO_READ")){
            Permissao permissao = new Permissao();
            permissao.setNome("MEMBRO_READ");
            permissaoRepository.save(permissao);
        }
        if (!permissaoRepository.existsByNome("POST_EDIT")){
            Permissao permissao = new Permissao();
            permissao.setNome("POST_EDIT");
            permissaoRepository.save(permissao);
        }
        if (!permissaoRepository.existsByNome("CANDIDATURA_EDIT")){
            Permissao permissao = new Permissao();
            permissao.setNome("CANDIDATURA_EDIT");
            permissaoRepository.save(permissao);
        }
        if (!permissaoRepository.existsByNome("MEMBRO_EDIT")){
            Permissao permissao = new Permissao();
            permissao.setNome("MEMBRO_EDIT");
            permissaoRepository.save(permissao);
        }
    }
}