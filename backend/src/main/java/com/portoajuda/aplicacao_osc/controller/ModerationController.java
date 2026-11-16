package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationAppealDecisionDTO;
import com.portoajuda.aplicacao_osc.dto.request.RequestModerationPointAdjustmentDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationAppealQueueDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationAppealDetailDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationEntityPageDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationSubjectDTO;
import com.portoajuda.aplicacao_osc.dto.response.ModerationRestrictionNoticeDTO;
import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.ModerationPointsService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ModerationController {
    private final ModerationPointsService moderation;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/moderation/me")
    public ResponseEntity<ModerationSubjectDTO> minhaPontuacao(@AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.myUserSubject(actor));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/moderation/organizations")
    public ResponseEntity<java.util.List<ModerationSubjectDTO>> managedOrganizations(@AuthenticationPrincipal Usuario actor){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.myManagedOrganizations(actor));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/osc/{oscId}/moderation")
    public ResponseEntity<ModerationSubjectDTO> pontosOsc(@PathVariable Integer oscId,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.ngoSubject(oscId,actor));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/osc/{oscId}/moderation/restriction")
    public ResponseEntity<ModerationRestrictionNoticeDTO> avisoRestricaoOsc(@PathVariable Integer oscId,
            @AuthenticationPrincipal Usuario actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.ngoRestrictionNotice(oscId,actor));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/moderation/appeals")
    public ResponseEntity<Void> recurso(@Valid @RequestBody RequestModerationAppealDTO request,
            @AuthenticationPrincipal Usuario actor) {
        moderation.fileAppeal(request.decisionHistoryId(),request.justificativa(),actor);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).REPORT_READ)")
    @GetMapping("/admin/moderation/capabilities")
    public ResponseEntity<Map<String,Boolean>> capabilities(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("pointsEnabled",moderation.isEnabled()));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).ENTITY_READ)")
    @GetMapping("/admin/moderation/entities")
    public ResponseEntity<ModerationEntityPageDTO> entities(@RequestParam String category,
            @RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.entities(category,q,page,size));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).POINTS_READ)")
    @GetMapping("/admin/moderation/subjects/{type}/{id}")
    public ResponseEntity<ModerationSubjectDTO> subject(@PathVariable String type,@PathVariable Integer id){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.adminSubject(type,id));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).POINTS_ADJUST)")
    @PostMapping("/admin/moderation/subjects/{type}/{id}/points")
    public ResponseEntity<Void> adjust(@PathVariable String type,@PathVariable Integer id,
            @Valid @RequestBody RequestModerationPointAdjustmentDTO request){
        moderation.adjust(type,id,request);return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).APPEAL_READ)")
    @GetMapping("/admin/moderation/appeals")
    public ResponseEntity<ModerationAppealQueueDTO> appeals(@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.appealQueue(status,page,size));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).APPEAL_READ)")
    @GetMapping("/admin/moderation/appeals/{id}")
    public ResponseEntity<ModerationAppealDetailDTO> appealDetail(@PathVariable long id){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.appealDetail(id));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).APPEAL_RESOLVE)")
    @PostMapping("/admin/moderation/appeals/{id}/decision")
    public ResponseEntity<Void> decideAppeal(@PathVariable long id,
            @Valid @RequestBody RequestModerationAppealDecisionDTO request){
        moderation.decideAppeal(id,request);return ResponseEntity.noContent().build();
    }
}
