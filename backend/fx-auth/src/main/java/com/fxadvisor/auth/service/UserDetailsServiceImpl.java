package com.fxadvisor.auth.service;

import com.fxadvisor.auth.entity.User;
import com.fxadvisor.auth.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads a User from MySQL and converts it to a Spring Security UserDetails.
 *
 * AUTHORITY LOADING — BOTH role names AND permission names are added:
 *
 *   For a ROLE_USER with PERMISSION_ANALYSE and PERMISSION_AUDIT_VIEW_OWN:
 *   Authorities = ["ROLE_USER", "PERMISSION_ANALYSE", "PERMISSION_AUDIT_VIEW_OWN"]
 *
 * WHY both? Spring Security's hasRole("USER") checks for "ROLE_USER".
 * @PreAuthorize("hasAuthority('PERMISSION_ANALYSE')") checks exact string match.
 * Including both gives flexibility for either check style.
 *
 * @Transactional ensures EAGER-loaded roles+permissions don't cause
 * LazyInitializationException if the JPA session closes before loading completes.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Add role names (e.g., ROLE_USER, ROLE_ADMIN)
        user.getRoles().forEach(role ->
                authorities.add(new SimpleGrantedAuthority(role.getName())));

        // Add permission names from all roles (e.g., PERMISSION_ANALYSE)
        user.getRoles().forEach(role ->
                role.getPermissions().forEach(perm ->
                        authorities.add(new SimpleGrantedAuthority(perm.getName()))));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(!user.isEnabled())
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}