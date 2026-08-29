package vikoba.service.dividend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.dividend.entity.Dividend;
import java.util.*;

public interface DividendRepository extends JpaRepository<Dividend, Long> {
    List<Dividend> findByGroupIdAndFinancialYearOrderByAmountDesc(Long groupId, Integer year);

    boolean existsByGroupIdAndFinancialYear(Long groupId, Integer year);
}
