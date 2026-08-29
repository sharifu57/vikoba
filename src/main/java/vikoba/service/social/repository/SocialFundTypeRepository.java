package vikoba.service.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.social.entity.SocialFundType;

import java.util.List;
import java.util.Optional;

public interface SocialFundTypeRepository extends JpaRepository<SocialFundType, Long> {
    List<SocialFundType> findByGroupIdAndActiveTrueOrderByNameAsc(Long groupId);

    Optional<SocialFundType> findByIdAndGroupId(Long id, Long groupId);

    boolean existsByGroupIdAndCode(Long groupId, String code);
}
