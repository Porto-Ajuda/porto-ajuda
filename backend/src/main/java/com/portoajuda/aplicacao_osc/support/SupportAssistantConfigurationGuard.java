package com.portoajuda.aplicacao_osc.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SupportAssistantConfigurationGuard implements ApplicationRunner {
    private final boolean enabled;
    private final boolean aiEnabled;
    private final String providerName;
    private final String apiKey;
    private final ObjectProvider<SupportAssistantProvider> providers;
    private final JdbcTemplate jdbc;

    public SupportAssistantConfigurationGuard(@Value("${app.support-assistant.enabled:false}") boolean enabled,
            @Value("${app.support-assistant.ai.enabled:false}") boolean aiEnabled,
            @Value("${app.support-assistant.ai.provider:none}") String providerName,
            @Value("${gemini.api-key:}") String apiKey, ObjectProvider<SupportAssistantProvider> providers,
            JdbcTemplate jdbc) {
        this.enabled=enabled; this.aiEnabled=aiEnabled; this.providerName=providerName; this.apiKey=apiKey;
        this.providers=providers; this.jdbc=jdbc;
    }

    @Override public void run(ApplicationArguments args) {
        if (!enabled) return;
        Boolean schema=jdbc.queryForObject("""
                SELECT to_regclass('public.support_conversations') IS NOT NULL
                   AND to_regclass('public.support_messages') IS NOT NULL
                   AND to_regclass('public.support_conversation_events') IS NOT NULL
                   AND to_regclass('public.support_retention_authorizations') IS NOT NULL
                   AND to_regprocedure('public.purge_expired_support_conversation(uuid,timestamptz)') IS NOT NULL
                """, Boolean.class);
        if (!Boolean.TRUE.equals(schema)) throw new IllegalStateException("Support assistant schema is not installed; keep SUPPORT_ASSISTANT_ENABLED=false.");
        if (aiEnabled && (providers.getIfAvailable()==null || providerName==null || providerName.isBlank() || "none".equalsIgnoreCase(providerName))) {
            throw new IllegalStateException("Support AI is enabled without an available provider adapter.");
        }
        if (aiEnabled && "gemini".equalsIgnoreCase(providerName) && (apiKey==null || apiKey.isBlank())) {
            throw new IllegalStateException("Support AI Gemini credential is not configured.");
        }
    }
}
