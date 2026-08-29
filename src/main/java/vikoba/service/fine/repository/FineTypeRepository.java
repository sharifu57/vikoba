package vikoba.service.fine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.fine.entity.FineType;

import java.util.Optional;

public interface FineTypeRepository extends JpaRepository<FineType, Long> {
    @Query("""
                SELECT ft FROM FineType ft
                WHERE ft.group.id = :groupId
                AND ft.code = :code
            """)
    Optional<FineType> findByGroupIdAndCode(@Param("groupId") Long groupId, @Param("code") String code);
}
