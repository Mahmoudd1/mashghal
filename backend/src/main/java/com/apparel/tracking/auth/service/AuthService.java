package com.apparel.tracking.auth.service;

import java.time.Instant;
import java.util.List;

import com.apparel.tracking.auth.domain.AppUser;
import com.apparel.tracking.auth.dto.AuthenticatedUserDto;
import com.apparel.tracking.auth.dto.LoginRequest;
import com.apparel.tracking.auth.dto.LoginResponse;
import com.apparel.tracking.auth.repository.AppUserRepository;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.config.AppProperties;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Username/password login issuing a signed JWT.
 *
 * <p>The token carries the user's role as a {@code roles} claim, which
 * {@code SecurityConfig} converts back into a Spring Security authority.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    public static final String ROLES_CLAIM = "roles";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties properties;

    public AuthService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.username())
                // Same error whether the user is unknown or the password is wrong, so
                // the response cannot be used to discover valid usernames.
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                .build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new LoginResponse(token, expiresAt, AuthenticatedUserDto.from(user));
    }

    public AuthenticatedUserDto currentUser(String username) {
        return users.findByUsernameIgnoreCase(username)
                .map(AuthenticatedUserDto::from)
                .orElseThrow(() -> new NotFoundException("No user named " + username));
    }
}
