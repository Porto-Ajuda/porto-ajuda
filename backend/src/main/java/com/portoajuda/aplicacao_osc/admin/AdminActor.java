package com.portoajuda.aplicacao_osc.admin;

import java.util.UUID;

/** Derived exclusively from the authenticated principal and filter-created session authority. */
public record AdminActor(Integer userId, UUID sessionId, AdminProfile profile) {
}
