package vikoba.service.dividend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.dividend.dto.*;
import vikoba.service.dividend.entity.Dividend;
import vikoba.service.dividend.repository.*;
import vikoba.service.contribution.entity.*;
import vikoba.service.contribution.repository.ShareTransactionRepository;
import vikoba.service.contribution.repository.PaymentRepository;
import vikoba.service.expense.repository.ExpenseRepository;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.fine.entity.Fine;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.*;
import java.math.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DividendService {
    private final DividendRepository dividends;
    private final VikobaGroupRepository groups;
    private final GroupMemberRepository members;
    private final ShareTransactionRepository shares;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final FineRepository fines;

    @Transactional(readOnly = true)
    public List<DividendResponse> list(Long groupId, Integer year) {
        return dividends.findByGroupIdAndFinancialYearOrderByAmountDesc(groupId, year).stream().map(this::map).toList();
    }

    @Transactional
    public List<DividendResponse> generate(Long groupId, DividendInput input) {
        BigDecimal profitPool = payments.findByGroupIdWithMember(groupId).stream()
                .filter(p -> p.getStatus() == vikoba.service.common.enums.PaymentStatus.COMPLETED)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(expenses.findByGroupIdWithCategory(groupId).stream()
                        .filter(e -> e.getStatus() == vikoba.service.common.enums.ExpenseStatus.PAID)
                        .map(vikoba.service.expense.entity.Expense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal unpaidFinePool = fines.findByGroupId(groupId).stream()
                .map(f -> f.getAmount().subtract(f.getPaidAmount() == null ? BigDecimal.ZERO : f.getPaidAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        profitPool = profitPool.add(unpaidFinePool);
        if (profitPool.signum() <= 0)
            throw new IllegalArgumentException("The system calculated no distributable profit for this group.");
        int year = input.getFinancialYear() == null ? java.time.Year.now().getValue() : input.getFinancialYear();
        if (dividends.existsByGroupIdAndFinancialYear(groupId, year))
            throw new IllegalArgumentException("Dividends already generated for this financial year.");
        var group = groups.findById(groupId).orElseThrow(() -> new IllegalArgumentException("Group not found."));
        var ledger = shares.findLedgerByGroupId(groupId);
        Map<Long, Integer> balances = new HashMap<>();
        for (var s : ledger) {
            int q = s.getType() == ShareTransactionType.REDEMPTION || s.getType() == ShareTransactionType.TRANSFER_OUT
                    ? -s.getQuantity()
                    : s.getQuantity();
            balances.merge(s.getGroupMember().getId(), q, Integer::sum);
        }
        int total = balances.values().stream().mapToInt(v -> Math.max(0, v)).sum();
        if (total <= 0)
            throw new IllegalArgumentException("No eligible shares found.");
        List<Dividend> saved = new ArrayList<>();
        for (var e : balances.entrySet()) {
            int owned = Math.max(0, e.getValue());
            if (owned == 0)
                continue;
            var gm = members.findById(e.getKey()).orElseThrow();
            BigDecimal assessed = fines.findByGroupId(groupId).stream().filter(f -> f.getGroupMember().getId().equals(gm.getId())).map(Fine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal finePaid = fines.findByGroupId(groupId).stream().filter(f -> f.getGroupMember().getId().equals(gm.getId())).map(f -> f.getPaidAmount() == null ? BigDecimal.ZERO : f.getPaidAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deduction = assessed.subtract(finePaid).max(BigDecimal.ZERO);
            BigDecimal gross = profitPool.multiply(BigDecimal.valueOf(owned)).divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN);
            BigDecimal contribution = payments.findByGroupIdWithMember(groupId).stream().filter(p -> p.getGroupMember() != null && p.getGroupMember().getId().equals(gm.getId()) && p.getStatus() == vikoba.service.common.enums.PaymentStatus.COMPLETED).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            saved.add(dividends.save(Dividend.builder().group(group).groupMember(gm).financialYear(year)
                    .profitPool(profitPool).contributions(contribution).finesAssessed(assessed).finesPaid(finePaid).fineDeduction(deduction).sharesOwned(owned).shareValue(BigDecimal.valueOf(owned)).amount(gross.subtract(deduction).max(BigDecimal.ZERO))
                    .build()));
        }
        return saved.stream().map(this::map).toList();
    }

    private DividendResponse map(Dividend d) {
        return DividendResponse.builder().id(d.getId()).groupMemberId(d.getGroupMember().getId())
                .memberName(d.getGroupMember().getMember().getFirstName() + " "
                        + d.getGroupMember().getMember().getLastName())
                .financialYear(d.getFinancialYear()).profitPool(d.getProfitPool()).sharesOwned(d.getSharesOwned())
                .contributions(d.getContributions()).finesAssessed(d.getFinesAssessed()).finesPaid(d.getFinesPaid()).fineDeduction(d.getFineDeduction()).shareValue(d.getShareValue()).amount(d.getAmount()).status(d.getStatus()).build();
    }
}
