package com.aiinterview.backend.auth.dto;

import com.aiinterview.backend.auth.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String name;

    @NotNull
    private Role role;
}
