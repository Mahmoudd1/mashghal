package com.apparel.tracking.auth.service;

import java.util.List;

import com.apparel.tracking.auth.domain.AppUser;
import com.apparel.tracking.auth.dto.AuthenticatedUserDto;
import com.apparel.tracking.auth.dto.UserRequest;
import com.apparel.tracking.auth.repository.AppUserRepository;
import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User administration. Admin-only, enforced by the URL rules in SecurityConfig. */
@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AuthenticatedUserDto> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(AuthenticatedUserDto::from).toList();
    }

    public AuthenticatedUserDto create(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("password_required", "A password is required for a new user");
        }
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessRuleException("username_taken",
                    "The username '%s' is already taken".formatted(request.username()));
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        user.setEnabled(request.enabled() == null || request.enabled());
        return AuthenticatedUserDto.from(users.save(user));
    }

    public AuthenticatedUserDto update(Long id, UserRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));

        if (!user.getUsername().equalsIgnoreCase(request.username())
                && users.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessRuleException("username_taken",
                    "The username '%s' is already taken".formatted(request.username()));
        }

        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        // A blank password means "leave it as it is".
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return AuthenticatedUserDto.from(user);
    }

    public void delete(Long id, String currentUsername) {
        AppUser user = users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));

        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new BusinessRuleException("cannot_delete_self", "You cannot delete your own account");
        }
        // Never leave the system without a way back in. Owners and admins both
        // count: either can administer, so the last of them is the last way in.
        if (user.getRole() != com.apparel.tracking.auth.domain.UserRole.DATA_ENTRY
                && countEnabledAdmins() <= 1) {
            throw new BusinessRuleException("last_admin",
                    "This is the only enabled administrator and cannot be removed");
        }
        users.delete(user);
    }

    private long countEnabledAdmins() {
        return users.findAll().stream()
                .filter(AppUser::isEnabled)
                .filter(user -> user.getRole() != com.apparel.tracking.auth.domain.UserRole.DATA_ENTRY)
                .count();
    }
}
