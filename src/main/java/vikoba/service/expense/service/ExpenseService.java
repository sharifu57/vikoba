package vikoba.service.expense.service;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
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
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.organization.service.ExpenseApprovalWorkflowService;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import vikoba.service.common.enums.GroupRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final VikobaGroupRepository groupRepository;
    private final GroupAuthorizationService authorizationService;
    private final ExpenseApprovalWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(Long groupId) {
        authorizationService.requireMembership(groupId);
        return expenseRepository.findByGroupIdWithCategory(groupId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long groupId, Long expenseId) {
        authorizationService.requireMembership(groupId);
        return toResponse(requireExpense(groupId, expenseId));
    }

    @Transactional
    public ExpenseResponse create(Long groupId, ExpenseRequest request) {
        authorizationService.requireMembership(groupId);
        VikobaGroup group = requireGroup(groupId);
        Expense expense = Expense.builder()
                .group(group)
                .category(resolveCategory(groupId, request))
                .reference(blankToNull(request.getReference()) == null ? generatedReference() : request.getReference().trim())
                .description(required(request.getDescription(), "description"))
                .amount(positive(request.getAmount()))
                .expenseDate(request.getExpenseDate() == null ? LocalDate.now() : request.getExpenseDate())
                .receiptNumber(blankToNull(request.getReceiptNumber()))
                .status(ExpenseStatus.PENDING)
                .build();
        expense.setApprovalStepsJson(writeSteps(workflowService.get(groupId).stream()
                .map(step -> new ExpenseApprovalStep(step.role().name(), step.label(), null, null)).toList()));
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(Long groupId, Long expenseId, ExpenseRequest request) {
        authorizationService.requirePermission(groupId, "CONTRIBUTION_MANAGE");
        Expense expense = requireExpense(groupId, expenseId);
        if (expense.getStatus() != ExpenseStatus.PENDING && expense.getStatus() != ExpenseStatus.REJECTED)
            throw new IllegalArgumentException("Approved expenses cannot be edited");
        if (request.getStatus() != null || request.getRejectionReason() != null)
            throw new IllegalArgumentException("Expense status changes must use the approval workflow");
        if (request.getCategoryId() != null || blankToNull(request.getCategoryName()) != null) expense.setCategory(resolveCategory(groupId, request));
        if (request.getDescription() != null) expense.setDescription(required(request.getDescription(), "description"));
        if (request.getAmount() != null) expense.setAmount(positive(request.getAmount()));
        if (request.getExpenseDate() != null) expense.setExpenseDate(request.getExpenseDate());
        if (request.getReceiptNumber() != null) expense.setReceiptNumber(blankToNull(request.getReceiptNumber()));
        if (request.getReference() != null) expense.setReference(required(request.getReference(), "reference"));
        if (expense.getStatus() == ExpenseStatus.REJECTED) {
            expense.setStatus(ExpenseStatus.PENDING);
            expense.setRejectionReason(null);
            expense.setApprovalStepsJson(writeSteps(workflowService.get(groupId).stream()
                    .map(step -> new ExpenseApprovalStep(step.role().name(), step.label(), null, null)).toList()));
        }
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long groupId, Long expenseId) {
        authorizationService.requirePermission(groupId, "CONTRIBUTION_MANAGE");
        Expense expense = requireExpense(groupId, expenseId);
        if (expense.getStatus() == ExpenseStatus.APPROVED || expense.getStatus() == ExpenseStatus.PAID)
            throw new IllegalArgumentException("Approved expenses cannot be deleted");
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public List<ShareApprovalStepConfig> approvalConfig(Long groupId) {
        authorizationService.requireMembership(groupId);
        return workflowService.get(groupId);
    }

    @Transactional
    public List<ShareApprovalStepConfig> configureApproval(Long groupId, List<ShareApprovalStepConfig> steps) {
        authorizationService.requirePermission(groupId, "WORKFLOW_MANAGE");
        workflowService.configure(requireGroup(groupId), steps);
        String configuredSteps = writeSteps(workflowService.get(groupId).stream()
                .map(step -> new ExpenseApprovalStep(step.role().name(), step.label(), null, null)).toList());
        // Retarget untouched pending requests when the group changes its reviewer flow.
        // An approval already in progress keeps its original history and order.
        for (Expense expense : expenseRepository.findByGroupIdWithCategory(groupId)) {
            if (expense.getStatus() == ExpenseStatus.PENDING && nextStep(readSteps(expense)) == 0) {
                expense.setApprovalStepsJson(configuredSteps);
                expenseRepository.save(expense);
            }
        }
        return workflowService.get(groupId);
    }

    @Transactional
    public ExpenseResponse approve(Long groupId, Long expenseId) {
        Expense expense = expenseRepository.findForUpdate(expenseId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found in this group"));
        if (expense.getStatus() != ExpenseStatus.PENDING) throw new IllegalArgumentException("Expense is not pending approval");
        var steps = new ArrayList<>(readSteps(expense));
        int index = nextStep(steps);
        if (index < 0) throw new IllegalArgumentException("Expense has no pending approval step");
        requireReviewer(groupId, steps.get(index));
        var current = steps.get(index);
        steps.set(index, new ExpenseApprovalStep(current.role(), current.label(), LocalDateTime.now().toString(),
                authorizationService.requireCurrentMembership(groupId).getId()));
        expense.setApprovalStepsJson(writeSteps(steps));
        if (nextStep(steps) < 0) expense.setStatus(ExpenseStatus.APPROVED);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse reject(Long groupId, Long expenseId, String reason) {
        String text = required(reason, "rejection reason");
        Expense expense = expenseRepository.findForUpdate(expenseId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found in this group"));
        if (expense.getStatus() != ExpenseStatus.PENDING) throw new IllegalArgumentException("Expense is not pending approval");
        var steps = readSteps(expense);
        int index = nextStep(steps);
        if (index < 0) throw new IllegalArgumentException("Expense has no pending approval step");
        requireReviewer(groupId, steps.get(index));
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectionReason(text);
        return toResponse(expenseRepository.save(expense));
    }

    private void requireReviewer(Long groupId, ExpenseApprovalStep step) {
        if (!authorizationService.hasRole(groupId, GroupRole.valueOf(step.role())))
            throw new AccessDeniedException("Only the configured " + step.role().replace('_', ' ') + " reviewer can approve this expense");
    }

    private int nextStep(List<ExpenseApprovalStep> steps) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).approvedAt() == null) return i;
        return -1;
    }

    private List<ExpenseApprovalStep> readSteps(Expense expense) {
        if (expense.getApprovalStepsJson() == null || expense.getApprovalStepsJson().isBlank())
            return configuredSteps(expense.getGroup().getId());
        try {
            List<ExpenseApprovalStep> saved = objectMapper.readValue(expense.getApprovalStepsJson(), new TypeReference<List<ExpenseApprovalStep>>() {});
            // A request nobody has acted on follows the current group configuration.
            if (expense.getStatus() == ExpenseStatus.PENDING && nextStep(saved) == 0)
                return configuredSteps(expense.getGroup().getId());
            return saved;
        }
        catch (Exception ex) { throw new IllegalStateException("Invalid saved expense approval steps", ex); }
    }

    private List<ExpenseApprovalStep> configuredSteps(Long groupId) {
        return workflowService.get(groupId).stream()
                .map(step -> new ExpenseApprovalStep(step.role().name(), step.label(), null, null)).toList();
    }

    private String writeSteps(List<ExpenseApprovalStep> steps) {
        try { return objectMapper.writeValueAsString(steps); }
        catch (Exception ex) { throw new IllegalStateException("Unable to save expense approval steps", ex); }
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> listCategories(Long groupId, boolean includeInactive) {
        List<ExpenseCategory> categories = includeInactive ? categoryRepository.findByGroupIsNullOrderByNameAsc() : categoryRepository.findByGroupIsNullAndActiveTrueOrderByNameAsc();
        return categories.stream().map(this::toCategoryResponse).toList();
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(Long groupId, ExpenseCategoryRequest request) {
        requireGroup(groupId);
        String name = required(request.getName(), "category name");
        if (categoryRepository.findByGroupIsNullAndNameIgnoreCase(name).isPresent()) throw new IllegalArgumentException("An expense category with this name already exists.");
        ExpenseCategory category = categoryRepository.save(ExpenseCategory.builder().name(name).description(blankToNull(request.getDescription())).active(request.getActive() == null || request.getActive()).build());
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
        if (request.getCategoryId() != null) return categoryRepository.findByIdAndGroupIsNull(request.getCategoryId()).orElseThrow(() -> new IllegalArgumentException("Expense category not found."));
        String name = required(request.getCategoryName(), "categoryName or categoryId");
        return categoryRepository.findByGroupIsNullAndNameIgnoreCase(name).orElseGet(() -> categoryRepository.save(ExpenseCategory.builder().name(name).active(true).build()));
    }

    private BigDecimal positive(BigDecimal value) { if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Expense amount must be greater than zero."); return value; }
    private String required(String value, String field) { String result = blankToNull(value); if (result == null) throw new IllegalArgumentException(field + " is required."); return result; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String generatedReference() { return "EXP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }
    private ExpenseResponse toResponse(Expense expense) { var steps = readSteps(expense); int index = nextStep(steps); boolean pending = expense.getStatus() == ExpenseStatus.PENDING && index >= 0; boolean canApprove = pending && authorizationService.hasRole(expense.getGroup().getId(), GroupRole.valueOf(steps.get(index).role())); return ExpenseResponse.builder().id(expense.getId()).groupId(expense.getGroup().getId()).categoryId(expense.getCategory().getId()).categoryName(expense.getCategory().getName()).reference(expense.getReference()).description(expense.getDescription()).amount(expense.getAmount()).expenseDate(expense.getExpenseDate()).receiptNumber(expense.getReceiptNumber()).status(expense.getStatus().name()).rejectionReason(expense.getRejectionReason()).approvalSteps(steps).currentStepLabel(pending ? steps.get(index).label() : null).canApprove(canApprove).createdAt(expense.getCreatedAt()).updatedAt(expense.getUpdatedAt()).build(); }
    private ExpenseCategoryResponse toCategoryResponse(ExpenseCategory category) { return ExpenseCategoryResponse.builder().id(category.getId()).groupId(category.getGroup() == null ? null : category.getGroup().getId()).name(category.getName()).description(category.getDescription()).active(category.isActive()).build(); }
}
