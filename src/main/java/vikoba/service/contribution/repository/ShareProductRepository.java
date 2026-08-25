package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.contribution.entity.ShareProduct;

import java.util.Optional;

public interface ShareProductRepository extends JpaRepository<ShareProduct, Long> {
    Optional<ShareProduct> findByGroupIdAndCode(Long groupId, String code);
}
