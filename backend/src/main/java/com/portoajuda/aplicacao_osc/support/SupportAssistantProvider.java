package com.portoajuda.aplicacao_osc.support;

import java.util.List;
import java.util.UUID;

/** Provider receives bounded user text and explicitly untrusted public-source excerpts only. */
public interface SupportAssistantProvider {
    Reply respond(Input input);

    /** Interprets the current turn against bounded history into server-owned capabilities. */
    default Interpretation interpret(InterpretationInput input) { return null; }

    enum FailureKind { CONFIGURATION, SHARED_BUDGET_LIMIT, QUOTA_OR_RATE_LIMIT, PROVIDER_ACCESS,
        PROVIDER_REJECTED, PROVIDER_SERVER, TIMEOUT, INVALID_RESPONSE, NETWORK }

    final class ProviderUnavailableException extends IllegalStateException {
        private final FailureKind kind;
        public ProviderUnavailableException(FailureKind kind) {
            super("assistência temporariamente indisponível");
            this.kind=kind==null?FailureKind.NETWORK:kind;
        }
        public FailureKind kind(){return kind;}
    }

    record Input(UUID conversationId, String latestQuestion, List<SupportDtos.Message> history,
                 List<SupportDtos.SearchResult> publicSources, boolean publicSearchUnavailable,
                 Interpretation interpretation, List<CapabilityOutcome> outcomes) {
        public Input(UUID conversationId, String latestQuestion, List<SupportDtos.Message> history,
                     List<SupportDtos.SearchResult> publicSources, boolean publicSearchUnavailable) {
            this(conversationId, latestQuestion, history, publicSources, publicSearchUnavailable, null, List.of());
        }
        public Input(UUID conversationId, String latestQuestion, List<SupportDtos.Message> history,
                     List<SupportDtos.SearchResult> publicSources, boolean publicSearchUnavailable, Interpretation interpretation) {
            this(conversationId, latestQuestion, history, publicSources, publicSearchUnavailable, interpretation, List.of());
        }
        public Input(UUID conversationId, String latestQuestion, List<SupportDtos.Message> history,
                     List<SupportDtos.SearchResult> publicSources) {
            this(conversationId, latestQuestion, history, publicSources, false, null, List.of());
        }
    }
    record Reply(String answer, List<String> sourceIds, boolean requestsHuman) {}
    record InterpretationInput(String latestQuestion, List<SupportDtos.Message> history) {}
    record Interpretation(List<CapabilityCall> capabilities, boolean clarificationNeeded,
                          String clarificationQuestion, boolean explicitHumanRequest,
                          boolean privacyBoundary, String boundaryKind) {}
    record CapabilityCall(String operation, String category, String query, String cause,
                          String city, String topic, String scope) {}
    record CapabilityOutcome(String operation, String category, String status, int resultCount) {}
}
