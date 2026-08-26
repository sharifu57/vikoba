package vikoba.service.loan.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.LoanProduct;
import java.util.*;
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> { List<LoanProduct> findByGroupIdAndActiveTrueOrderByNameAsc(Long groupId); Optional<LoanProduct> findByIdAndGroupId(Long id, Long groupId); }
