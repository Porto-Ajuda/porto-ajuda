package com.portoajuda.aplicacao_osc.support;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/support-assistant")
@Validated
public class SupportAssistantController {
    private final SupportAssistantService support;
    public SupportAssistantController(SupportAssistantService support) { this.support = support; }

    @GetMapping("/intro")
    public ResponseEntity<SupportDtos.Intro> intro() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.intro());
    }

    /** Returns only the authenticated user's local preference namespace; the preference itself stays on-device. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/voice-preference-scope")
    public ResponseEntity<SupportDtos.VoicePreferenceScope> voicePreferenceScope(@AuthenticationPrincipal Usuario user) {
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SupportDtos.VoicePreferenceScope(user.getId()));
    }

    @GetMapping("/public-search")
    public ResponseEntity<List<SupportDtos.SearchResult>> search(
            @RequestParam String query, @RequestParam(defaultValue = "ALL") String category) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.publicSearch(query, category));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations")
    public ResponseEntity<SupportDtos.Conversation> create(@AuthenticationPrincipal Usuario user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(support.create(user));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/new")
    public ResponseEntity<SupportDtos.Conversation> startNew(
            @Valid @RequestBody SupportDtos.NewConversationRequest request,
            @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(support.startNewConversation(request, user));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/conversations")
    public ResponseEntity<List<SupportDtos.ConversationSummary>> mine(@AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.mine(user));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/conversations/{id}")
    public ResponseEntity<SupportDtos.Conversation> mine(@PathVariable UUID id, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.mine(id, user));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<SupportDtos.Conversation> send(@PathVariable UUID id,
            @Valid @RequestBody SupportDtos.UserMessageRequest request, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.send(id, user, request));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/retry-ai")
    public ResponseEntity<SupportDtos.Conversation> retryAi(@PathVariable UUID id, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.retryAi(id, user));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/request-human")
    public ResponseEntity<SupportDtos.Conversation> requestHuman(@PathVariable UUID id,
            @Valid @RequestBody SupportDtos.ActionRequest request, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.requestHuman(id, user, request));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/continue-with-sophie")
    public ResponseEntity<SupportDtos.Conversation> continueWithSophie(
            @PathVariable UUID id, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.continueWithSophie(id, user));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/reopen")
    public ResponseEntity<SupportDtos.Conversation> reopen(@PathVariable UUID id, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.reopen(id, user));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/close")
    public ResponseEntity<SupportDtos.Conversation> close(@PathVariable UUID id, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(support.closeMine(id, user));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/conversations/{id}/volunteer-application-drafts")
    public ResponseEntity<List<SupportDtos.VolunteerApplicationDraft>> pendingVolunteerApplications(
            @PathVariable UUID id, @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(support.pendingVolunteerApplications(id, user));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/volunteer-application-drafts/{draftId}/confirm")
    public ResponseEntity<SupportDtos.VolunteerApplicationDraft> confirmVolunteerApplication(
            @PathVariable UUID id, @PathVariable UUID draftId,
            @Valid @RequestBody SupportDtos.ActionConfirmationRequest request,
            @AuthenticationPrincipal Usuario user) {
        SupportDtos.VolunteerApplicationDraft result = support.confirmVolunteerApplication(
                id, draftId, request.requestHash(), user);
        return ResponseEntity.status("EXPIRED".equals(result.state()) ? HttpStatus.GONE : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/conversations/{id}/volunteer-application-drafts/{draftId}/cancel")
    public ResponseEntity<SupportDtos.VolunteerApplicationDraft> cancelVolunteerApplication(
            @PathVariable UUID id, @PathVariable UUID draftId,
            @Valid @RequestBody SupportDtos.ActionConfirmationRequest request,
            @AuthenticationPrincipal Usuario user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                support.cancelVolunteerApplication(id, draftId, request.requestHash(), user));
    }

}
