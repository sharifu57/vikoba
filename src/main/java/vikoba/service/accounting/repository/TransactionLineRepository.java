package vikoba.service.accounting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.accounting.entity.TransactionLine;

import java.util.List;

public interface TransactionLineRepository extends JpaRepository<TransactionLine, Long> {
    @Query("select l from TransactionLine l join fetch l.transaction t join fetch l.account a where t.group.id = :groupId order by t.transactionDate asc, l.id asc")
    List<TransactionLine> findLedgerByGroupId(@Param("groupId") Long groupId);
}
