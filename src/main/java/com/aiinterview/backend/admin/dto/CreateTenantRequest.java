package com.aiinterview.backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Admin name is required")
    private String adminName;

    @Email(message = "Valid email required")
    @NotBlank
    private String adminEmail;

    @NotBlank
    @Size(min = 8)
    private String password;

    private String plan = "trial";
}
