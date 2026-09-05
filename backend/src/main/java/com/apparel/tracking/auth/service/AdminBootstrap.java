package com.apparel.tracking.auth.service;

import com.apparel.tracking.auth.domain.AppUser;
import com.apparel.tracking.auth.domain.UserRole;
import com.apparel.tracking.auth.repository.AppUserRepository;
import com.apparel.tracking.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first account so a fresh database is reachable. It is an OWNER:
 * the person setting the system up is the one who should see the money.
 *
 * <p>Runs only when there are no users at all, so it never resets a password
 * somebody has since changed. It logs when it skips for that reason: the variable
 * looks like a password setter and is not one, and silence there is genuinely
 * confusing to anyone deploying.
 */
@Component
// Must run before any other seeding: those create users of their own, and this
// only fires when the user table is completely empty.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = users.count();
        if (existing > 0) {
            // Says so out loud, because the alternative is baffling: setting
            // APP_ADMIN_PASSWORD on a system that already has users looks like it
            // should change the password, and silently does nothing.
            log.info("Skipping owner bootstrap: {} user(s) already exist. APP_ADMIN_PASSWORD seeds "
                    + "only the very first account and has no effect now — change passwords through "
                    + "the Users screen.", existing);
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(properties.bootstrap().adminUsername());
        admin.setPasswordHash(passwordEncoder.encode(properties.bootstrap().adminPassword()));
        admin.setDisplayName("المالك");
        admin.setRole(UserRole.OWNER);
        users.save(admin);

        log.warn("Created the initial owner account '{}'. Change its password before going live.",
                admin.getUsername());
    }
}
