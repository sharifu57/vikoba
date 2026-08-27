package vikoba.service.auth.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.auth.entity.UserRole;
import java.util.List;
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
 @Query("select distinct ur from UserRole ur join fetch ur.role r left join fetch r.permissions where ur.user.phone = :phone")
 List<UserRole> findByUserPhoneWithPermissions(@Param("phone") String phone);
}
