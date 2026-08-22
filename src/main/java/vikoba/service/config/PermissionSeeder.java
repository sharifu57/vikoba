package vikoba.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vikoba.service.auth.entity.Permission;
import vikoba.service.auth.repository.PermissionRepository;

@Component
@RequiredArgsConstructor
public class PermissionSeeder implements CommandLineRunner {
        private final PermissionRepository permissionRepository;

        @Override
        public void run(String... args) {
                createPermission(
                                "CREATE_MEMBER",
                                "Create Group Member");


        }

        private void createPermission(
                        String name,
                        String description) {
                if (!permissionRepository.existsByName(name)) {
                        Permission permission = new Permission();
                        permission.setName(name);
                        permission.setDescription(description);
                        permissionRepository.save(permission);
                }
        }
}
