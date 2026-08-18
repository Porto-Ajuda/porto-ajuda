package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.request.RequestLoginDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestUsuarioDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseLoginDTO;
import com.portoajuda.aplicacao_osc.dto.response.ResponseUsuarioDTO;
import com.portoajuda.aplicacao_osc.entity.Role;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.enums.Genero;
import com.portoajuda.aplicacao_osc.repository.RoleRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.JwtService;
import com.portoajuda.aplicacao_osc.utils.Cpf;
import com.portoajuda.aplicacao_osc.utils.Email;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final RoleRepository roleRepository;

    @Transactional
    public void signup(RequestUsuarioDTO usuarioDTO){
        if(usuarioRepository.existsByEmail(new Email(usuarioDTO.email()))){
            throw new BadCredentialsException("Email já está cadastrado");
        }
        int idade = Period.between(
                usuarioDTO.dataNascimento(),
                LocalDate.now()
        ).getYears();
        if (idade < 16){
            throw new BadCredentialsException("A idade mínima é de 16 anos");
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

        Role role = roleRepository.findByNome("USUARIO")
                .orElseThrow(() -> new IllegalArgumentException("Role não encontrada"));

        usuario.setRoles(Set.of(role));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public ResponseLoginDTO login(RequestLoginDTO loginDTO){
        Usuario usuario = usuarioRepository.findByEmail(new Email(loginDTO.email())).
                orElseThrow(() -> new IllegalArgumentException("Email ou senha incorretos"));
        if(!passwordEncoder.matches(loginDTO.senha(), usuario.getSenha())){
            throw new BadCredentialsException("Email ou senha incorretos");
        }

        return new ResponseLoginDTO(jwtService.generateToken(usuario),
                new ResponseUsuarioDTO(usuario.getCpf().valor(), usuario.getNome(),
                        usuario.getNomeSocial(), usuario.getDataNascimento().toString(), usuario.getEmail().valor(), usuario.getTelefone())
        );
    }

    @Transactional
    public void delete(Usuario usuario){
        if(!usuarioRepository.existsById(usuario.getId())){
            throw new IllegalArgumentException("Usuário não existe");
        }

        usuarioRepository.deleteById(usuario.getId());
    }

    @Transactional
    public void update(RequestUsuarioDTO usuarioDTO, Usuario usuario){
        Usuario usuarioAlterado = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));
        usuarioAlterado.setEmail(new Email(usuarioDTO.email()));
        usuarioAlterado.setTelefone(usuarioDTO.telefone());
        usuarioRepository.save(usuarioAlterado);
    }

    @Transactional
    public void changePassword(String senha, Integer id){
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

        if(senha == null || senha.isBlank()){
            throw new BadCredentialsException("Senha não pode ser vazia");
        }

        usuario.setSenha(passwordEncoder.encode(senha));
    }

    public ResponseUsuarioDTO view(Usuario usuario){
        Usuario viewUsuario = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não existe"));

        return new ResponseUsuarioDTO(viewUsuario.getCpf().valor(), viewUsuario.getNome(),
                viewUsuario.getNomeSocial(), viewUsuario.getDataNascimento().toString(), viewUsuario.getEmail().valor(), viewUsuario.getTelefone());
    }
}