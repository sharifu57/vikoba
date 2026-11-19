package vikoba.service.fine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.fine.entity.FineType;

import java.util.Optional;

public interface FineTypeRepository extends JpaRepository<FineType, Long> {
    @Query("""
                SELECT ft FROM FineType ft
                WHERE ft.group IS NULL AND ft.code = :code
            """)
    Optional<FineType> findGlobalByCode(@Param("code") String code);

    @Query("""
                SELECT ft FROM FineType ft
                WHERE ft.group.id = :groupId AND ft.code = :code
            """)
    Optional<FineType> findByGroupIdAndCode(@Param("groupId") Long groupId, @Param("code") String code);

    @Query("""
                SELECT ft FROM FineType ft
                WHERE (ft.group.id = :groupId OR ft.group IS NULL)
                AND ft.code = :code
                ORDER BY CASE WHEN ft.group IS NULL THEN 1 ELSE 0 END
            """)
    Optional<FineType> findAvailableByGroupIdAndCode(@Param("groupId") Long groupId, @Param("code") String code);

    java.util.List<FineType> findByGroupIdOrderByNameAsc(Long groupId);

    @Query("""
                SELECT ft FROM FineType ft
                WHERE ft.group.id = :groupId
                   OR (ft.group IS NULL AND NOT EXISTS (
                       SELECT localType.id FROM FineType localType
                       WHERE localType.group.id = :groupId AND localType.code = ft.code
                   ))
                ORDER BY ft.name ASC
            """)
    java.util.List<FineType> findAvailableByGroupIdOrderByNameAsc(@Param("groupId") Long groupId);
}
