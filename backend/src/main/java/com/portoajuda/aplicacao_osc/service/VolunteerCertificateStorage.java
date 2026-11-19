package com.portoajuda.aplicacao_osc.service;

import java.time.Instant;

public interface VolunteerCertificateStorage {
    String store(Integer oscId, Integer applicationId, String extension, String contentType, byte[] content);
    SignedAccess createShortLivedAccess(String objectKey);
    record SignedAccess(String url, Instant expiresAt) {}
}
