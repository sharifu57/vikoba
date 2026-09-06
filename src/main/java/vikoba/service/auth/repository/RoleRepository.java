package vikoba.service.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.auth.entity.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    @Query("select distinct r from Role r left join fetch r.permissions where r.name = :name")
    Optional<Role> findByNameWithPermissions(@Param("name") String name);
}
