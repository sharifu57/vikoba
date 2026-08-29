package vikoba.service.expense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.expense.entity.ExpenseCategory;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    List<ExpenseCategory> findByGroupIdOrderByNameAsc(Long groupId);
    List<ExpenseCategory> findByGroupIdAndActiveTrueOrderByNameAsc(Long groupId);
    Optional<ExpenseCategory> findByIdAndGroupId(Long id, Long groupId);
    Optional<ExpenseCategory> findByGroupIdAndNameIgnoreCase(Long groupId, String name);
}
