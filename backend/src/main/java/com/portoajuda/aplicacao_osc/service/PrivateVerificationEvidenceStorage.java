package com.portoajuda.aplicacao_osc.service;

import java.time.Instant;

/** Private object storage boundary; URLs are issued only after application authorization. */
public interface PrivateVerificationEvidenceStorage {
    String store(int oscId, long applicationId, int version, String extension, String mediaType, byte[] bytes);
    byte[] readPrivate(String objectKey, long maximumBytes);
    SignedAccess createShortLivedAccess(String objectKey);
    void delete(String objectKey);

    record SignedAccess(String url, Instant expiresAt) {
    }
}
