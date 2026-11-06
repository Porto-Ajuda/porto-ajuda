package com.portoajuda.aplicacao_osc.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.portoajuda.aplicacao_osc.controller.PhoneVerificationController;
import com.portoajuda.aplicacao_osc.repository.PhoneVerificationChallengeRepository;
import com.portoajuda.aplicacao_osc.repository.UsuarioRepository;
import com.portoajuda.aplicacao_osc.segurity.ClientIpResolver;
import com.portoajuda.aplicacao_osc.service.AdminAuthAuditService;
import com.portoajuda.aplicacao_osc.service.AdminAuthCodeSecurity;
import com.portoajuda.aplicacao_osc.service.AdminAuthControlsService;
import com.portoajuda.aplicacao_osc.service.AdminAuthDeliveryQueueService;
import com.portoajuda.aplicacao_osc.service.AdminAuthDeliveryWorker;
import com.portoajuda.aplicacao_osc.service.EmailService;
import com.portoajuda.aplicacao_osc.service.PhoneVerificationService;
import com.portoajuda.aplicacao_osc.service.SmsMaisClient;
import com.portoajuda.aplicacao_osc.service.SmsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class SmsMaisApplicationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(
                    SmsMaisHttpConfiguration.class,
                    SmsMaisClient.class,
                    SmsService.class,
                    AdminAuthDeliveryWorker.class,
                    PhoneVerificationService.class,
                    PhoneVerificationController.class)
            .withPropertyValues(
                    "smsmais.token=sbx_context_test_only",
                    "smsmais.environment=sandbox")
            .withBean(UsuarioRepository.class, org.mockito.Mockito::mock)
            .withBean(PhoneVerificationChallengeRepository.class, org.mockito.Mockito::mock)
            .withBean(AdminAuthCodeSecurity.class, org.mockito.Mockito::mock)
            .withBean(AdminAuthControlsService.class, org.mockito.Mockito::mock)
            .withBean(AdminAuthAuditService.class, org.mockito.Mockito::mock)
            .withBean(AdminAuthDeliveryQueueService.class, org.mockito.Mockito::mock)
            .withBean(EmailService.class, org.mockito.Mockito::mock)
            .withBean(PasswordEncoder.class, org.mockito.Mockito::mock)
            .withBean(ClientIpResolver.class, org.mockito.Mockito::mock)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void smsMaisAndPhoneVerificationChainStartsWithoutDatabaseOrExternalCalls() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
            assertEquals(1, context.getBeansOfType(RestClient.Builder.class).size());
            assertNotNull(context.getBean(RestClient.Builder.class));
            assertNotNull(context.getBean("smsMaisRestClient", RestClient.class));
            assertNotNull(context.getBean(SmsMaisClient.class));
            assertNotNull(context.getBean(SmsService.class));
            assertNotNull(context.getBean(AdminAuthDeliveryWorker.class));
            assertNotNull(context.getBean(PhoneVerificationService.class));
            assertNotNull(context.getBean(PhoneVerificationController.class));
        });
    }
}
