package com.watchtower.watchtower.config;

import com.watchtower.watchtower.entity.Role;
import com.watchtower.watchtower.entity.User;
import com.watchtower.watchtower.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds two demo accounts on first startup, since the doc's endpoint table
 * has no registration endpoint - only POST /auth/login. Credentials are
 * intentionally simple and documented in the README; this is local/demo
 * data in the same spirit as the synthetic incident generator, not a
 * production user store.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("User table already populated, skipping seed");
            return;
        }

        userRepository.save(new User("viewer", passwordEncoder.encode("viewer123"), Role.VIEWER));
        userRepository.save(new User("approver", passwordEncoder.encode("approver123"), Role.APPROVER));
        log.info("Seeded demo users: viewer/viewer123 (VIEWER), approver/approver123 (APPROVER)");
    }
}
