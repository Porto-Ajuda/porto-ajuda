package com.portoajuda.aplicacao_osc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Logs only effective, non-secret settings needed to diagnose admin MFA deployments. */
@Component
public class AdminAuthRuntimeDiagnostics implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuthRuntimeDiagnostics.class);
    private final boolean smsRequired;
    private final String smsEnvironment;
    private final boolean sandboxCodeAccess;
    private final boolean secureCookies;
    private final boolean deliveryWorkerEnabled;
    private final long deliveryPollMs;
    private final boolean rbacEnforcementEnabled;

    public AdminAuthRuntimeDiagnostics(
            @Value("${admin-auth.sms-verification-enabled:true}") boolean smsRequired,
            @Value("${smsmais.environment:sandbox}") String smsEnvironment,
            @Value("${admin-auth.sandbox-code-access-enabled:false}") boolean sandboxCodeAccess,
            @Value("${app.cookies.secure:true}") boolean secureCookies,
            @Value("${app.admin-auth.delivery-worker.enabled:true}") boolean deliveryWorkerEnabled,
            @Value("${admin-auth.delivery-poll-ms:1000}") long deliveryPollMs,
            @Value("${app.admin.rbac.enforcement-enabled:false}") boolean rbacEnforcementEnabled) {
        this.smsRequired = smsRequired;
        this.smsEnvironment = smsEnvironment;
        this.sandboxCodeAccess = sandboxCodeAccess;
        this.secureCookies = secureCookies;
        this.deliveryWorkerEnabled = deliveryWorkerEnabled;
        this.deliveryPollMs = deliveryPollMs;
        this.rbacEnforcementEnabled = rbacEnforcementEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Admin MFA runtime: factors=email+sms, smsRequired={}, smsEnvironment={}, "
                        + "sandboxCodeAccess={}, cookieSecure={}, deliveryWorkerEnabled={}, deliveryPollMs={}, "
                        + "rbacEnforcementEnabled={}",
                smsRequired, smsEnvironment, sandboxCodeAccess, secureCookies,
                deliveryWorkerEnabled, deliveryPollMs, rbacEnforcementEnabled);
    }
}
