package com.portoajuda.aplicacao_osc.support;

import com.portoajuda.aplicacao_osc.admin.AdminPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrative support endpoints live below /admin so the existing MFA cookie is sent and validated. */
@RestController
@RequestMapping("/admin/support-assistant")
@Validated
public class SupportAssistantAdminController {
    private final SupportAssistantService support;

    public SupportAssistantAdminController(SupportAssistantService support) { this.support=support; }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_MANAGE)")
    @GetMapping("/eligible-staff")
    public ResponseEntity<List<SupportDtos.EligibleStaff>> eligibleStaff() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.eligibleStaff());
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_READ)")
    @GetMapping("/conversations")
    public ResponseEntity<SupportDtos.Page> queue(@RequestParam(defaultValue="WAITING_HUMAN") String state,
            @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.staffQueue(state,page,size));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_READ)")
    @GetMapping("/conversations/{id}")
    public ResponseEntity<SupportDtos.Conversation> detail(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.staffDetail(id));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_MANAGE)")
    @PostMapping("/conversations/{id}/take-over")
    public ResponseEntity<SupportDtos.Conversation> takeOver(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.takeOver(id));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_MANAGE)")
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<SupportDtos.Conversation> reply(@PathVariable UUID id,
            @Valid @RequestBody SupportDtos.ReplyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.staffReply(id,request));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_MANAGE)")
    @PostMapping("/conversations/{id}/transfer")
    public ResponseEntity<SupportDtos.Conversation> transfer(@PathVariable UUID id,
            @Valid @RequestBody SupportDtos.TransferRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.transfer(id,request));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_MANAGE)")
    @PostMapping("/conversations/{id}/return-to-ai")
    public ResponseEntity<SupportDtos.Conversation> returnToAi(@PathVariable UUID id,
            @Valid @RequestBody SupportDtos.ActionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.returnToAi(id,request));
    }

    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).SUPPORT_MANAGE)")
    @PostMapping("/conversations/{id}/close")
    public ResponseEntity<SupportDtos.Conversation> close(@PathVariable UUID id,
            @Valid @RequestBody SupportDtos.ActionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.closeByStaff(id,request));
    }
}
