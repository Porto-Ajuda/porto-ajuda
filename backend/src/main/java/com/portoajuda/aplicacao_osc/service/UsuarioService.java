package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {
    private final Argon2PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(Argon2PasswordEncoder passwordEncoder, UsuarioRepository usuarioRepository) {
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ResponseUsuarioDTO cadastro(RequestUsuarioDTO usuarioDTO){
        if(usuarioRepository.existsByEmail(new Email(usuarioDTO.email()))){
            throw new IllegalArgumentException("Email já está cadastrado");
        }
        Usuario usuario = new Usuario();
        usuario.setCpf(new Cpf(usuarioDTO.cpf()));
        usuario.setNome(usuarioDTO.nome());
        usuario.setNomeSocial(usuarioDTO.nomeSocial());
        usuario.setDataNascimento(usuarioDTO.dataNascimento());
        usuario.setEmail(new Email(usuarioDTO.email()));
        usuario.setGenero(Genero.valueOf(usuarioDTO.genero()));
        usuario.setTelefone(usuarioDTO.telefone());
        usuario.setSenha(passwordEncoder.encode(usuarioDTO.senha()));

        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        return new ResponseUsuarioDTO
                (usuarioSalvo.getId(),
                usuarioSalvo.getNome(),
                usuarioSalvo.getNomeSocial(),
                usuarioSalvo.getEmail().valor(),
                usuarioSalvo.getTelefone());
    }

    @Transactional
    public void excluir(Integer id){
        if(!usuarioRepository.existsById(id)){
            throw new IllegalArgumentException("Usuário não existe");
        }

        usuarioRepository.deleteById(id);
    }

    @Transactional
    public void alterar(RequestUsuarioDTO usuarioDTO, Integer id){
        Usuario usuarioAlterado = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));
        usuarioAlterado.setEmail(new Email(usuarioDTO.email()));
        usuarioAlterado.setTelefone(usuarioDTO.telefone());
        usuarioRepository.save(usuarioAlterado);
    }

    @Transactional
    public void alterarSenha(String senha, Integer id){
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

        if(senha == null || senha.isBlank()){
            throw new IllegalArgumentException("Senha não pode ser vazia");
        }

        usuario.setSenha(passwordEncoder.encode(senha));
    }

    public ResponseUsuarioDTO visualizar(Integer id){
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

        return new ResponseUsuarioDTO(usuario.getId(), usuario.getNome(),
                usuario.getNomeSocial(), usuario.getEmail().valor(), usuario.getTelefone());
    }
}