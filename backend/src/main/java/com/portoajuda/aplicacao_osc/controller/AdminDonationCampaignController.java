package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.DonationCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/donation-campaigns")
@RequiredArgsConstructor
public class AdminDonationCampaignController {
    private final DonationCampaignService campaigns;
    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_EDIT_NEWS)")
    @PostMapping("/{id}/suspension") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable long id,@RequestBody ModerationInput input,@AuthenticationPrincipal Usuario actor){ campaigns.moderate(id,actor,input.reason(),true); }
    @PreAuthorize("@adminAuthorization.hasPermission(authentication, T(com.portoajuda.aplicacao_osc.admin.AdminPermission).CONTENT_EDIT_NEWS)")
    @PostMapping("/{id}/restoration") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable long id,@RequestBody ModerationInput input,@AuthenticationPrincipal Usuario actor){ campaigns.moderate(id,actor,input.reason(),false); }
    public record ModerationInput(String reason) {}
}
