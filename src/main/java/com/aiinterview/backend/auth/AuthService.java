package com.aiinterview.backend.auth;

import com.aiinterview.backend.auth.dto.*;
import com.aiinterview.backend.company.Company;
import com.aiinterview.backend.company.CompanyRepository;
import com.aiinterview.backend.notifications.EmailService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public AuthResponse registerCompany(RegisterCompanyRequest req) {
        if (userRepository.existsByEmail(req.getAdminEmail())) {
            throw new IllegalStateException("Email already in use: " + req.getAdminEmail());
        }

        String slug = generateUniqueSlug(req.getCompanyName());

        Company company = new Company();
        company.setName(req.getCompanyName());
        company.setSlug(slug);
        company.setPlan("trial");
        company.setActive(true);
        company = companyRepository.save(company);

        User user = new User();
        user.setCompanyId(company.getId());
        user.setName(req.getAdminName());
        user.setEmail(req.getAdminEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.COMPANY_ADMIN);
        user.setActive(true);
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        return buildAuthResponse(user, company, token);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("No account found for: " + req.getEmail()));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid password");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));

        String token = jwtUtil.generateToken(user);
        return buildAuthResponse(user, company, token);
    }

    @Transactional
    public void inviteUser(InviteUserRequest req, Long companyId) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalStateException("Email already registered: " + req.getEmail());
        }

        String token = UUID.randomUUID().toString();

        InviteToken invite = new InviteToken();
        invite.setToken(token);
        invite.setEmail(req.getEmail());
        invite.setName(req.getName());
        invite.setRole(req.getRole().name());
        invite.setCompanyId(companyId);
        invite.setExpiresAt(LocalDateTime.now().plusHours(48));
        invite.setUsed(false);
        inviteTokenRepository.save(invite);

        String inviteLink = frontendUrl + "/accept-invite?token=" + token;
        emailService.sendInviteEmail(req.getEmail(), req.getName(), inviteLink);

        System.out.println("[Auth] Invite sent to " + req.getEmail());
    }

    @Transactional
    public AuthResponse acceptInvite(AcceptInviteRequest req) {
        InviteToken invite = inviteTokenRepository.findByToken(req.getToken())
                .orElseThrow(() -> new EntityNotFoundException("Invalid invite token"));

        if (invite.isUsed()) {
            throw new IllegalStateException("Invite token has already been used");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Invite token has expired");
        }
        if (userRepository.existsByEmail(invite.getEmail())) {
            throw new IllegalStateException("Email already registered: " + invite.getEmail());
        }

        User user = new User();
        user.setCompanyId(invite.getCompanyId());
        user.setName(req.getName());
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.valueOf(invite.getRole()));
        user.setActive(true);
        user = userRepository.save(user);

        invite.setUsed(true);
        inviteTokenRepository.save(invite);

        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));

        String token = jwtUtil.generateToken(user);
        return buildAuthResponse(user, company, token);
    }

    public AuthResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new EntityNotFoundException("Company not found"));
        return buildAuthResponse(user, company, null);
    }

    private String generateUniqueSlug(String companyName) {
        String base = companyName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-");

        if (companyRepository.findBySlug(base).isEmpty()) {
            return base;
        }
        int suffix = 2;
        while (companyRepository.findBySlug(base + "-" + suffix).isPresent()) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private AuthResponse buildAuthResponse(User user, Company company, String token) {
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .companyId(user.getCompanyId())
                .companyName(company.getName())
                .build();
    }
}
