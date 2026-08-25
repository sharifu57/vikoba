package vikoba.service.expense.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.expense.dto.*;
import vikoba.service.expense.service.ExpenseService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/expenses/group/{groupId}")
public class ExpenseController {
    private final ExpenseService expenseService;

    @GetMapping public ResponseEntity<ApiResponse<List<ExpenseResponse>>> list(@PathVariable Long groupId) { return ResponseEntity.ok(ApiResponse.success("Expenses retrieved successfully.", expenseService.list(groupId))); }
    @GetMapping("/{expenseId}") public ResponseEntity<ApiResponse<ExpenseResponse>> get(@PathVariable Long groupId, @PathVariable Long expenseId) { return ResponseEntity.ok(ApiResponse.success("Expense retrieved successfully.", expenseService.get(groupId, expenseId))); }
    @PostMapping public ResponseEntity<ApiResponse<ExpenseResponse>> create(@PathVariable Long groupId, @RequestBody ExpenseRequest request) { return ResponseEntity.ok(ApiResponse.success("Expense recorded successfully.", expenseService.create(groupId, request))); }
    @PutMapping("/{expenseId}") public ResponseEntity<ApiResponse<ExpenseResponse>> update(@PathVariable Long groupId, @PathVariable Long expenseId, @RequestBody ExpenseRequest request) { return ResponseEntity.ok(ApiResponse.success("Expense updated successfully.", expenseService.update(groupId, expenseId, request))); }
    @DeleteMapping("/{expenseId}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long groupId, @PathVariable Long expenseId) { expenseService.delete(groupId, expenseId); return ResponseEntity.ok(ApiResponse.success("Expense deleted successfully.", null)); }
    @GetMapping("/categories") public ResponseEntity<ApiResponse<List<ExpenseCategoryResponse>>> categories(@PathVariable Long groupId, @RequestParam(defaultValue = "false") boolean includeInactive) { return ResponseEntity.ok(ApiResponse.success("Expense categories retrieved successfully.", expenseService.listCategories(groupId, includeInactive))); }
    @PostMapping("/categories") public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> createCategory(@PathVariable Long groupId, @RequestBody ExpenseCategoryRequest request) { return ResponseEntity.ok(ApiResponse.success("Expense category created successfully.", expenseService.createCategory(groupId, request))); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
    }
}
