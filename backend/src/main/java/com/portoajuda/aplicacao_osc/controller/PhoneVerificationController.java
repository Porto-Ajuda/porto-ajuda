package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.dto.request.PhoneVerificationCodeRequest;
import com.portoajuda.aplicacao_osc.dto.request.PhoneVerificationStartRequest;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.PhoneVerificationService;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuario/telefone/verificacao")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;
    private final ClientIpResolver clientIpResolver;

    @PreAuthorize("hasAnyRole('USUARIO', 'ADMIN')")
    @PostMapping
    public ResponseEntity<Map<String, Object>> iniciar(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody PhoneVerificationStartRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        UUID challengeId = phoneVerificationService.iniciar(
                usuario.getId(), request.senha(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("challengeId", challengeId,
                        "mensagem", "Se o telefone puder ser confirmado, um código será enviado."));
    }

    @PreAuthorize("hasAnyRole('USUARIO', 'ADMIN')")
    @PostMapping("/{challengeId}")
    public ResponseEntity<?> confirmar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID challengeId,
            @Valid @RequestBody PhoneVerificationCodeRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        boolean verified = phoneVerificationService.confirmar(
                usuario.getId(), challengeId, request.codigo(), clientIpResolver.resolve(httpRequest));
        if (!verified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("mensagem", "Código inválido ou expirado."));
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
