package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestOscDTO;
import com.portoajuda.aplicacao_osc.dto.request.UpdateOscDTO;
import com.portoajuda.aplicacao_osc.entity.Osc;
import com.portoajuda.aplicacao_osc.entity.OscMembros;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.TipoChavePix;
import com.portoajuda.aplicacao_osc.repository.OscMembrosRepository;
import com.portoajuda.aplicacao_osc.repository.OscRepository;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cnpj;
import com.portoajuda.aplicacao_osc.utils.Email;
import io.jsonwebtoken.Jwts;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OscService {
    private final OscRepository oscRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    @Transactional
    public void create(RequestOscDTO oscDTO, Usuario usuarioAuth) {
        if (oscRepository.existsByCnpj(new Cnpj(oscDTO.cnpj()))) {
            throw new BadCredentialsException("Cnpj já cadastrado");
        }
        Osc osc = new Osc();
        osc.setCnpj(new Cnpj(oscDTO.cnpj()));
        osc.setCausas(oscDTO.causas());
        osc.setNome(oscDTO.nome());
        osc.setChavePix(oscDTO.pix());
        osc.setTipoChavePix(TipoChavePix.valueOf(oscDTO.tipoPix()));
        osc.setDescricao(oscDTO.descricao());
        osc.setEmail(new Email(oscDTO.email()));
        osc.setTelefone(oscDTO.telefone());
        osc.setSite(oscDTO.site());
        osc.setCidade(oscDTO.cidade());
        osc.setCep(oscDTO.cep());


        Usuario usuario = usuarioRepository.findByEmail(usuarioAuth.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        Role role = roleRepository.findByNome("OSC")
                .orElseThrow(() -> new IllegalArgumentException("Role não encontrada"));

        usuario.getRoles().add(role);
        osc.setUsuario(usuario);

        oscRepository.save(osc);
    }

    @Transactional
    public void update(UpdateOscDTO updateOscDTO){
        Osc osc = oscRepository.findByEmail(new Email(updateOscDTO.email()))
                .orElseThrow(() -> new IllegalArgumentException("Cnpj não encontrado"));
        osc.setChavePix(updateOscDTO.pix());
        osc.setTipoChavePix(TipoChavePix.valueOf(updateOscDTO.tipoPix()));
        osc.setDescricao(updateOscDTO.descricao());
        osc.setEmail(new Email(updateOscDTO.email()));
        osc.setTelefone(updateOscDTO.telefone());
        osc.setSite(updateOscDTO.site());
        oscRepository.save(osc);
    }

    @Transactional
    public void delete(Cnpj cnpj, Integer id) {
        if(!oscRepository.existsByCnpj(cnpj)){
            throw new BadCredentialsException("Erro ao encontrar CNPJ");
        }
        Osc osc = oscRepository.findByCnpj(cnpj)
                .orElseThrow(() -> new IllegalArgumentException("Osc não encontrada"));
        if (osc.getId().equals(id)){
            oscRepository.delete(osc);
        }
        else {
            throw new BadCredentialsException("Você não é permitido de fazer esta operação");
        }
    }

    public Page<Osc> viewAll(Pageable pageable){
        return oscRepository.findAll(pageable);
    }
}