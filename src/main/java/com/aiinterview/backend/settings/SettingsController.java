package com.aiinterview.backend.settings;

import com.aiinterview.backend.auth.AuthenticatedUser;
import com.aiinterview.backend.auth.User;
import com.aiinterview.backend.auth.UserRepository;
import com.aiinterview.backend.common.ApiResponse;
import com.aiinterview.backend.common.SecurityUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserRepository userRepository;

    @GetMapping("/team")
    public ResponseEntity<ApiResponse<List<TeamMemberDto>>> getTeamMembers() {
        AuthenticatedUser user = SecurityUtils.getCurrentUser();

        List<User> members = userRepository.findAllByCompanyId(user.companyId());

        List<TeamMemberDto> dtos = members.stream()
                .map(m -> new TeamMemberDto(
                        m.getId(),
                        m.getName(),
                        m.getEmail(),
                        m.getRole().name(),
                        m.isActive()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @Data
    @AllArgsConstructor
    static class TeamMemberDto {
        Long id;
        String name;
        String email;
        String role;
        Boolean active;
    }
}
