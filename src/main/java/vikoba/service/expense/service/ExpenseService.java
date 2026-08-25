package vikoba.service.expense.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.ExpenseStatus;
import vikoba.service.expense.dto.*;
import vikoba.service.expense.entity.Expense;
import vikoba.service.expense.entity.ExpenseCategory;
import vikoba.service.expense.repository.ExpenseCategoryRepository;
import vikoba.service.expense.repository.ExpenseRepository;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final VikobaGroupRepository groupRepository;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(Long groupId) {
        requireGroup(groupId);
        return expenseRepository.findByGroupIdWithCategory(groupId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long groupId, Long expenseId) {
        return toResponse(requireExpense(groupId, expenseId));
    }

    @Transactional
    public ExpenseResponse create(Long groupId, ExpenseRequest request) {
        VikobaGroup group = requireGroup(groupId);
        Expense expense = Expense.builder()
                .group(group)
                .category(resolveCategory(groupId, request))
                .reference(blankToNull(request.getReference()) == null ? generatedReference() : request.getReference().trim())
                .description(required(request.getDescription(), "description"))
                .amount(positive(request.getAmount()))
                .expenseDate(request.getExpenseDate() == null ? LocalDate.now() : request.getExpenseDate())
                .receiptNumber(blankToNull(request.getReceiptNumber()))
                .status(parseStatus(request.getStatus()))
                .rejectionReason(blankToNull(request.getRejectionReason()))
                .build();
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(Long groupId, Long expenseId, ExpenseRequest request) {
        Expense expense = requireExpense(groupId, expenseId);
        if (request.getCategoryId() != null || blankToNull(request.getCategoryName()) != null) expense.setCategory(resolveCategory(groupId, request));
        if (request.getDescription() != null) expense.setDescription(required(request.getDescription(), "description"));
        if (request.getAmount() != null) expense.setAmount(positive(request.getAmount()));
        if (request.getExpenseDate() != null) expense.setExpenseDate(request.getExpenseDate());
        if (request.getReceiptNumber() != null) expense.setReceiptNumber(blankToNull(request.getReceiptNumber()));
        if (request.getReference() != null) expense.setReference(required(request.getReference(), "reference"));
        if (request.getStatus() != null) expense.setStatus(parseStatus(request.getStatus()));
        if (request.getRejectionReason() != null) expense.setRejectionReason(blankToNull(request.getRejectionReason()));
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long groupId, Long expenseId) {
        expenseRepository.delete(requireExpense(groupId, expenseId));
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> listCategories(Long groupId, boolean includeInactive) {
        requireGroup(groupId);
        List<ExpenseCategory> categories = includeInactive ? categoryRepository.findByGroupIdOrderByNameAsc(groupId) : categoryRepository.findByGroupIdAndActiveTrueOrderByNameAsc(groupId);
        return categories.stream().map(this::toCategoryResponse).toList();
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(Long groupId, ExpenseCategoryRequest request) {
        VikobaGroup group = requireGroup(groupId);
        String name = required(request.getName(), "category name");
        if (categoryRepository.findByGroupIdAndNameIgnoreCase(groupId, name).isPresent()) throw new IllegalArgumentException("An expense category with this name already exists.");
        ExpenseCategory category = categoryRepository.save(ExpenseCategory.builder().group(group).name(name).description(blankToNull(request.getDescription())).active(request.getActive() == null || request.getActive()).build());
        return toCategoryResponse(category);
    }

    private VikobaGroup requireGroup(Long groupId) {
        if (groupId == null) throw new IllegalArgumentException("groupId is required.");
        return groupRepository.findById(groupId).orElseThrow(() -> new IllegalArgumentException("Group not found."));
    }

    private Expense requireExpense(Long groupId, Long expenseId) {
        if (expenseId == null) throw new IllegalArgumentException("expenseId is required.");
        return expenseRepository.findByIdAndGroupIdWithCategory(expenseId, groupId).orElseThrow(() -> new IllegalArgumentException("Expense not found in this group."));
    }

    private ExpenseCategory resolveCategory(Long groupId, ExpenseRequest request) {
        if (request.getCategoryId() != null) return categoryRepository.findByIdAndGroupId(request.getCategoryId(), groupId).orElseThrow(() -> new IllegalArgumentException("Expense category not found in this group."));
        String name = required(request.getCategoryName(), "categoryName or categoryId");
        return categoryRepository.findByGroupIdAndNameIgnoreCase(groupId, name).orElseGet(() -> categoryRepository.save(ExpenseCategory.builder().group(requireGroup(groupId)).name(name).active(true).build()));
    }

    private ExpenseStatus parseStatus(String value) {
        if (blankToNull(value) == null) return ExpenseStatus.PENDING;
        try { return ExpenseStatus.valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid expense status."); }
    }
    private BigDecimal positive(BigDecimal value) { if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Expense amount must be greater than zero."); return value; }
    private String required(String value, String field) { String result = blankToNull(value); if (result == null) throw new IllegalArgumentException(field + " is required."); return result; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String generatedReference() { return "EXP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }
    private ExpenseResponse toResponse(Expense expense) { return ExpenseResponse.builder().id(expense.getId()).groupId(expense.getGroup().getId()).categoryId(expense.getCategory().getId()).categoryName(expense.getCategory().getName()).reference(expense.getReference()).description(expense.getDescription()).amount(expense.getAmount()).expenseDate(expense.getExpenseDate()).receiptNumber(expense.getReceiptNumber()).status(expense.getStatus().name()).rejectionReason(expense.getRejectionReason()).createdAt(expense.getCreatedAt()).updatedAt(expense.getUpdatedAt()).build(); }
    private ExpenseCategoryResponse toCategoryResponse(ExpenseCategory category) { return ExpenseCategoryResponse.builder().id(category.getId()).groupId(category.getGroup().getId()).name(category.getName()).description(category.getDescription()).active(category.isActive()).build(); }
}
