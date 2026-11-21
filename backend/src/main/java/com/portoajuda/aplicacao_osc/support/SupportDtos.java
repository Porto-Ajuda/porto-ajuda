package com.portoajuda.aplicacao_osc.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SupportDtos {
    private SupportDtos() {}

    public record Intro(String title, String description, List<String> topics, String privacyNotice,
                        String assistantName,
                        boolean conversationsEnabled, boolean aiAvailable) {}
    public record SearchResult(String id, String category, String title, String summary, String href) {}
    public record VolunteerApplicationDraft(java.util.UUID id, Integer opportunityId, String title, String ngoName,
                                            String details, String requestHash, Instant expiresAt,
                                            String state, Integer applicationId, String applicationStatus,
                                            String message) {}
    public record VoicePreferenceScope(Integer userId) {}
    public record ConversationSummary(UUID id, String state, Instant createdAt, Instant updatedAt, Instant stateChangedAt,
                                      Instant lastMessageAt, boolean unread, long version,
                                      String firstUserMessage, String lastMessagePreview) {}
    public record Message(long id, String senderType, String senderLabel, String body, Instant createdAt) {}
    public record Transition(String eventType, String fromState, String toState, String actorType, Instant createdAt) {}
    public record Conversation(UUID id, String state, String assignedStaffLabel, Instant createdAt,
                               Instant updatedAt, Instant closedAt, Instant contentExpiresAt,
                               long version, boolean unread, List<Message> messages, List<Transition> transitions) {}
    public record UserMessageRequest(@NotBlank @Size(max = 4000) String body,
                                     @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencyKey) {}
    public record NewConversationRequest(@NotNull UUID conversationId, UUID previousConversationId) {}
    public record ActionConfirmationRequest(@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String requestHash) {}
    public record EligibleStaff(Integer userId, String displayName) {}
    public record ActionRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record ReplyRequest(@NotBlank @Size(max = 4000) String body,
                               @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String idempotencyKey) {}
    public record TransferRequest(@NotNull Integer targetStaffUserId, @NotBlank @Size(max = 1000) String reason) {}
    public record Page(List<ConversationSummary> items, int page, int size, boolean hasNext) {}
}
