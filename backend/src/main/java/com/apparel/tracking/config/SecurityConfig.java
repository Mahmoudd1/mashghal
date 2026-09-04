package com.apparel.tracking.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.apparel.tracking.auth.domain.UserRole;
import com.apparel.tracking.auth.service.AuthService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security.
 *
 * <p>Authorisation is expressed as URL rules rather than annotations, because the
 * two roles split cleanly along HTTP verbs:
 * <ul>
 *   <li>reads — both roles</li>
 *   <li>pipeline actions (receiving, moves, sales, flags) — both roles</li>
 *   <li>everything else that writes, and every delete — ADMIN only</li>
 * </ul>
 * Rule order matters: the pipeline exception is declared before the catch-all
 * write rule that follows it.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // The built single-page app and its assets. The app itself
                        // guards its routes; the API below is what actually holds
                        // the data behind authentication.
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/favicon.ico",
                                "/*.js", "/*.css", "/*.map", "/*.webmanifest",
                                "/assets/**", "/media/**", "/i18n/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/{path:^(?!api$|actuator$).*}").permitAll()

                        // Data entry records production activity.
                        .requestMatchers(HttpMethod.POST, "/api/pipeline/**").authenticated()

                        // Everything else that writes is administration.
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(ADMIN)

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    /** Reads the token's {@code roles} claim and prefixes each with {@code ROLE_}. */
    private JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AuthService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private SecretKey signingKey() {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * OWNER is a superset of ADMIN, which is a superset of DATA_ENTRY. Declaring
     * it once here means the URL rules stay written in terms of the least role
     * that should pass, rather than listing every role that happens to qualify.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(UserRole.OWNER.name()).implies(UserRole.ADMIN.name())
                .role(UserRole.ADMIN.name()).implies(UserRole.DATA_ENTRY.name())
                .build();
    }

    /** Applies the hierarchy to @PreAuthorize as well as the URL rules. */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(signingKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
