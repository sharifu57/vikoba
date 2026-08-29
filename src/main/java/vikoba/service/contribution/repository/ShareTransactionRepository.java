package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.contribution.entity.ShareTransaction;

import java.util.List;

public interface ShareTransactionRepository extends JpaRepository<ShareTransaction, Long> {
    @Query("""
            SELECT st FROM ShareTransaction st
            JOIN FETCH st.groupMember gm
            JOIN FETCH gm.member m
            JOIN FETCH st.shareProduct sp
            WHERE sp.group.id = :groupId
            ORDER BY st.transactionDate DESC, st.id DESC
            """)
    List<ShareTransaction> findLedgerByGroupId(@Param("groupId") Long groupId);
}
