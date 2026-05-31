package com.aiinterview.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInviteRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    private String name;
}
