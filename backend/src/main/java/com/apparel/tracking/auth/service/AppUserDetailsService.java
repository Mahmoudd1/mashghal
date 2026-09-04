package com.apparel.tracking.auth.service;

import java.util.List;

import com.apparel.tracking.auth.domain.AppUser;
import com.apparel.tracking.auth.repository.AppUserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user named " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().authority())))
                .disabled(!user.isEnabled())
                .build();
    }
}
