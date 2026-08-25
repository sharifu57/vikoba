package vikoba.service.expense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.expense.entity.Expense;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @Query("""
            SELECT e FROM Expense e
            JOIN FETCH e.group
            JOIN FETCH e.category
            WHERE e.group.id = :groupId
            ORDER BY e.expenseDate DESC, e.id DESC
            """)
    List<Expense> findByGroupIdWithCategory(@Param("groupId") Long groupId);

    @Query("""
            SELECT e FROM Expense e
            JOIN FETCH e.group
            JOIN FETCH e.category
            WHERE e.id = :expenseId AND e.group.id = :groupId
            """)
    Optional<Expense> findByIdAndGroupIdWithCategory(@Param("expenseId") Long expenseId, @Param("groupId") Long groupId);
}
