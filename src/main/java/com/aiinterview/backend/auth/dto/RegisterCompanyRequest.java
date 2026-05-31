package com.aiinterview.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCompanyRequest {

    @NotBlank
    private String companyName;

    @NotBlank
    private String adminName;

    @Email
    @NotBlank
    private String adminEmail;

    @NotBlank
    @Size(min = 8)
    private String password;
}
