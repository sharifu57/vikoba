package vikoba.service.report.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.FineStatus;
import vikoba.service.common.enums.LoanStatus;
import vikoba.service.contribution.dto.ContributionDetailResponse;
import vikoba.service.contribution.dto.PaymentResponse;
import vikoba.service.contribution.dto.ShareTransactionResponse;
import vikoba.service.contribution.service.ContributionService;
import vikoba.service.contribution.service.PaymentService;
import vikoba.service.contribution.service.ShareService;
import vikoba.service.expense.dto.ExpenseResponse;
import vikoba.service.expense.service.ExpenseService;
import vikoba.service.fine.entity.Fine;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.loan.entity.Loan;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.meeting.repository.MeetingRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.report.dto.GroupReportResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GroupReportService {
    private final VikobaGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final ContributionService contributionService;
    private final PaymentService paymentService;
    private final ShareService shareService;
    private final ExpenseService expenseService;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final MeetingRepository meetingRepository;

    @Transactional(readOnly = true)
    public GroupReportResponse generate(Long groupId, LocalDate start, LocalDate end) {
        if (groupId == null)
            throw new IllegalArgumentException("groupId is required.");
        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));
        LocalDate from = start == null ? LocalDate.now().withDayOfMonth(1) : start;
        LocalDate to = end == null ? LocalDate.now() : end;
        if (to.isBefore(from))
            throw new IllegalArgumentException("Report end date cannot be before start date.");

        List<ContributionDetailResponse> contributions = contributionService.getGroupContributionDetails(groupId, null,
                null);
        List<PaymentResponse> payments = paymentService.list(groupId);
        List<ShareTransactionResponse> shares = shareService.getLedger(groupId);
        List<ExpenseResponse> expenses = expenseService.list(groupId);
        List<Loan> loans = loanRepository.findByGroupId(groupId);
        List<Fine> fines = fineRepository.findByGroupId(groupId);

        List<ContributionDetailResponse> periodContributions = contributions.stream()
                .filter(item -> inRange(
                        item.getPaidAt() == null ? item.getPeriodStart() : item.getPaidAt().toLocalDate(), from, to))
                .toList();
        List<PaymentResponse> periodPayments = payments.stream().filter(
                item -> inRange(item.getPaymentDate() == null ? null : item.getPaymentDate().toLocalDate(), from, to))
                .toList();
        List<ExpenseResponse> periodExpenses = expenses.stream()
                .filter(item -> inRange(item.getExpenseDate(), from, to)).toList();

        BigDecimal contributionTotal = sum(
                periodContributions.stream().map(ContributionDetailResponse::getPaidAmount).toList());
        BigDecimal paymentIncome = sum(periodPayments.stream().filter(item -> "COMPLETED".equals(item.getStatus()))
                .map(PaymentResponse::getAmount).toList());
        BigDecimal expenseTotal = sum(periodExpenses.stream().filter(item -> "APPROVED".equals(item.getStatus()))
                .map(ExpenseResponse::getAmount).toList());
        BigDecimal shareCapital = shares.stream()
                .filter(item -> "PURCHASE".equals(item.getType()) || "TRANSFER_IN".equals(item.getType()))
                .map(ShareTransactionResponse::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(shares.stream()
                        .filter(item -> "REDEMPTION".equals(item.getType()) || "TRANSFER_OUT".equals(item.getType()))
                        .map(ShareTransactionResponse::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal loanOutstanding = loans.stream()
                .filter(item -> item.getStatus() == LoanStatus.ACTIVE || item.getStatus() == LoanStatus.DISBURSED)
                .map(Loan::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finesOutstanding = fines.stream()
                .filter(item -> item.getStatus() == FineStatus.UNPAID || item.getStatus() == FineStatus.PARTIAL)
                .map(item -> item.getAmount().subtract(item.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return GroupReportResponse.builder().groupId(groupId).groupName(group.getName()).periodStart(from).periodEnd(to)
                .summary(GroupReportResponse.Summary.builder()
                        .members(memberRepository.countActiveMembersByGroupId(groupId)).contributions(contributionTotal)
                        .shareCapital(shareCapital).loanOutstanding(loanOutstanding).finesOutstanding(finesOutstanding)
                        .income(paymentIncome).expenses(expenseTotal).netIncome(paymentIncome.subtract(expenseTotal))
                        .activeLoans(loans.stream()
                                .filter(item -> item.getStatus() == LoanStatus.ACTIVE
                                        || item.getStatus() == LoanStatus.DISBURSED)
                                .count())
                        .unpaidFines(fines.stream()
                                .filter(item -> item.getStatus() == FineStatus.UNPAID
                                        || item.getStatus() == FineStatus.PARTIAL)
                                .count())
                        .meetings(meetingRepository.findByGroupIdOrderByMeetingDateDesc(groupId).stream()
                                .filter(item -> inRange(item.getMeetingDate(), from, to)).count())
                        .build())
                .monthlyTotals(monthlyTotals(periodContributions, periodPayments, periodExpenses))
                .memberBalances(memberBalances(contributions, fines, loans))
                .recentTransactions(recentTransactions(periodPayments, periodExpenses))
                .build();
    }

    private List<GroupReportResponse.MonthlyTotal> monthlyTotals(List<ContributionDetailResponse> contributions,
            List<PaymentResponse> payments, List<ExpenseResponse> expenses) {
        Map<YearMonth, BigDecimal[]> totals = new LinkedHashMap<>();
        contributions.forEach(
                item -> add(totals, item.getPaidAt() == null ? item.getPeriodStart() : item.getPaidAt().toLocalDate(),
                        0, item.getPaidAmount()));
        payments.forEach(item -> add(totals, item.getPaymentDate() == null ? null : item.getPaymentDate().toLocalDate(),
                1, item.getAmount()));
        expenses.forEach(item -> add(totals, item.getExpenseDate(), 2, item.getAmount()));
        return totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> GroupReportResponse.MonthlyTotal.builder().month(entry.getKey().toString())
                        .contributions(entry.getValue()[0]).payments(entry.getValue()[1]).expenses(entry.getValue()[2])
                        .build())
                .toList();
    }

    private List<GroupReportResponse.MemberBalance> memberBalances(List<ContributionDetailResponse> contributions,
            List<Fine> fines, List<Loan> loans) {
        Map<Long, GroupReportResponse.MemberBalance> result = new LinkedHashMap<>();
        contributions.forEach(item -> result.computeIfAbsent(item.getGroupMemberId(),
                id -> GroupReportResponse.MemberBalance.builder().groupMemberId(id).memberName(item.getMemberName())
                        .membershipNumber(item.getMemberAccountNumber()).contributions(BigDecimal.ZERO)
                        .fines(BigDecimal.ZERO).loanBalance(BigDecimal.ZERO).build())
                .setContributions(value(result.get(item.getGroupMemberId()).getContributions())
                        .add(value(item.getPaidAmount()))));
        fines.forEach(item -> {
            GroupMember member = item.getGroupMember();
            GroupReportResponse.MemberBalance row = result.computeIfAbsent(member.getId(),
                    id -> GroupReportResponse.MemberBalance.builder().groupMemberId(id)
                            .memberName(member.getMember().getFirstName() + " " + member.getMember().getLastName())
                            .membershipNumber(member.getMembershipNumber()).contributions(BigDecimal.ZERO)
                            .fines(BigDecimal.ZERO).loanBalance(BigDecimal.ZERO).build());
            row.setFines(row.getFines().add(value(item.getAmount()).subtract(value(item.getPaidAmount()))));
        });
        loans.forEach(item -> {
            GroupMember member = item.getGroupMember();
            GroupReportResponse.MemberBalance row = result.computeIfAbsent(member.getId(),
                    id -> GroupReportResponse.MemberBalance.builder().groupMemberId(id)
                            .memberName(member.getMember().getFirstName() + " " + member.getMember().getLastName())
                            .membershipNumber(member.getMembershipNumber()).contributions(BigDecimal.ZERO)
                            .fines(BigDecimal.ZERO).loanBalance(BigDecimal.ZERO).build());
            if (item.getStatus() == LoanStatus.ACTIVE || item.getStatus() == LoanStatus.DISBURSED)
                row.setLoanBalance(row.getLoanBalance().add(value(item.getTotalAmount())));
        });
        return new ArrayList<>(result.values());
    }

    private List<GroupReportResponse.ActivityRow> recentTransactions(List<PaymentResponse> payments,
            List<ExpenseResponse> expenses) {
        List<GroupReportResponse.ActivityRow> rows = new ArrayList<>();
        payments.forEach(item -> rows.add(GroupReportResponse.ActivityRow.builder()
                .date(item.getPaymentDate() == null ? null : item.getPaymentDate().toLocalDate().toString())
                .reference(item.getReference()).memberName(item.getMemberName()).category(item.getAllocationType())
                .amount(item.getAmount()).status(item.getStatus()).build()));
        expenses.forEach(item -> rows.add(GroupReportResponse.ActivityRow.builder()
                .date(item.getExpenseDate() == null ? null : item.getExpenseDate().toString())
                .reference(item.getReference()).memberName("Group expense").category(item.getCategoryName())
                .amount(item.getAmount().negate()).status(item.getStatus()).build()));
        return rows.stream().sorted(Comparator.comparing(GroupReportResponse.ActivityRow::getDate,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(50).toList();
    }

    private void add(Map<YearMonth, BigDecimal[]> totals, LocalDate date, int index, BigDecimal amount) {
        if (date == null)
            return;
        totals.computeIfAbsent(YearMonth.from(date),
                ignored -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO })[index] = totals
                        .get(YearMonth.from(date))[index].add(value(amount));
    }

    private boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().map(this::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
