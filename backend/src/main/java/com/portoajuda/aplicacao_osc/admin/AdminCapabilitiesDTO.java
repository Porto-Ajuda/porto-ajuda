package com.portoajuda.aplicacao_osc.admin;

import java.util.List;

public record AdminCapabilitiesDTO(AdminProfile profile, List<AdminPermission> permissions) {
    public AdminCapabilitiesDTO {
        permissions = List.copyOf(permissions);
    }
}
