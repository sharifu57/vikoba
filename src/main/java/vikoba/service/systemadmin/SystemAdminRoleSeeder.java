package vikoba.service.systemadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.auth.entity.Role;
import vikoba.service.auth.repository.RoleRepository;

@Component
@RequiredArgsConstructor
public class SystemAdminRoleSeeder implements CommandLineRunner {
    private final RoleRepository roles;
    @Override
    @Transactional
    public void run(String... args) {
        if (roles.findByName("SUPER_ADMIN").isEmpty()) {
            roles.save(Role.builder().name("SUPER_ADMIN")
                    .description("System administrator: all-group visibility and chairperson appointments").build());
        }
    }
}
