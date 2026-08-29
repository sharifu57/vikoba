package vikoba.service.organization.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.contribution.service.ContributionService;
import vikoba.service.contribution.service.PaymentService;
import vikoba.service.contribution.service.ShareService;
import vikoba.service.contribution.dto.ContributionDetailResponse;
import vikoba.service.contribution.dto.PaymentResponse;
import vikoba.service.contribution.dto.ShareTransactionResponse;
import vikoba.service.fine.entity.Fine;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.meeting.entity.Meeting;
import vikoba.service.meeting.repository.MeetingRepository;
import vikoba.service.common.enums.LoanStatus;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.common.enums.FineStatus;
import vikoba.service.organization.dto.DashboardStatistic;
import vikoba.service.organization.dto.DashboardOverviewResponse;
import vikoba.service.organization.repository.GroupMemberRepository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final GroupMemberRepository groupMemberRepository;
    private final MemberContributionRepository memberContributionRepository;
    private final LoanRepository loanRepository;
    private final ContributionService contributionService;
    private final ShareService shareService;
    private final PaymentService paymentService;
    private final MeetingRepository meetingRepository;
    private final FineRepository fineRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public DashboardStatistic getStatisticsForGroup(Long groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required");
        }

        Long totalMembers = groupMemberRepository.countActiveMembersByGroupId(groupId);

        BigDecimal totalContributions = memberContributionRepository.sumPaidAmountByGroupId(groupId);
        if (totalContributions == null)
            totalContributions = BigDecimal.ZERO;

        // Compute net shares: purchases + transfers_in - transfers_out - redemptions
        Long purchased = entityManager.createQuery(
                "SELECT COALESCE(SUM(st.quantity), 0) FROM ShareTransaction st WHERE st.shareProduct.group.id = :groupId AND st.type IN :inTypes",
                Long.class)
                .setParameter("groupId", groupId)
                .setParameter("inTypes", Arrays.asList(ShareTransactionType.PURCHASE, ShareTransactionType.TRANSFER_IN))
                .getSingleResult();

        Long sold = entityManager.createQuery(
                "SELECT COALESCE(SUM(st.quantity), 0) FROM ShareTransaction st WHERE st.shareProduct.group.id = :groupId AND st.type IN :outTypes",
                Long.class)
                .setParameter("groupId", groupId)
                .setParameter("outTypes",
                        Arrays.asList(ShareTransactionType.TRANSFER_OUT, ShareTransactionType.REDEMPTION))
                .getSingleResult();

        BigDecimal totalShares = BigDecimal.valueOf(Math.max(0L, purchased - sold));

        // Sum outstanding loans for the group (disbursed or active)
        BigDecimal totalOutstandingLoans = entityManager.createQuery(
                "SELECT COALESCE(SUM(l.totalAmount), 0) FROM Loan l WHERE l.groupMember.group.id = :groupId AND l.status IN :statuses",
                BigDecimal.class)
                .setParameter("groupId", groupId)
                .setParameter("statuses", Arrays.asList(LoanStatus.DISBURSED, LoanStatus.ACTIVE))
                .getSingleResult();

        if (totalOutstandingLoans == null)
            totalOutstandingLoans = BigDecimal.ZERO;

        return new DashboardStatistic(totalMembers, totalContributions, totalShares, totalOutstandingLoans);
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(Long groupId) {
        if (groupId == null)
            throw new IllegalArgumentException("groupId is required");

        List<ContributionDetailResponse> contributions = contributionService.getGroupContributionDetails(groupId, null,
                null);
        List<ShareTransactionResponse> shares = shareService.getLedger(groupId);
        List<PaymentResponse> payments = paymentService.list(groupId);
        List<Meeting> meetings = meetingRepository.findUpcomingByGroupId(groupId);
        List<?> loans = loanRepository.findByGroupId(groupId);
        List<Fine> fines = fineRepository.findByGroupId(groupId);

        BigDecimal contributionTotal = contributions.stream()
                .map(item -> zeroIfNull(item.getPaidAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        BigDecimal shareCapital = shares.stream()
                .filter(item -> item.getType().equals("PURCHASE") || item.getType().equals("TRANSFER_IN"))
                .map(item -> zeroIfNull(item.getTotalAmount()))
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right))
                .subtract(shares.stream()
                        .filter(item -> item.getType().equals("REDEMPTION") || item.getType().equals("TRANSFER_OUT"))
                        .map(item -> zeroIfNull(item.getTotalAmount()))
                        .reduce(BigDecimal.ZERO, (left, right) -> left.add(right)));

        List<DashboardOverviewResponse.TrendPoint> contributionTrend = trend(contributions.stream()
                .filter(item -> item.getPeriodStart() != null)
                .collect(Collectors.groupingBy(item -> YearMonth.from(item.getPeriodStart()),
                        Collectors.reducing(BigDecimal.ZERO,
                                item -> item.getPaidAmount() == null ? BigDecimal.ZERO : item.getPaidAmount(),
                                (left, right) -> left.add(right)))));
        List<DashboardOverviewResponse.TrendPoint> shareTrend = trend(shares.stream()
                .filter(item -> item.getTransactionDate() != null && item.getType().equals("PURCHASE"))
                .collect(Collectors.groupingBy(item -> YearMonth.from(item.getTransactionDate().toLocalDate()),
                        Collectors.reducing(BigDecimal.ZERO, item -> zeroIfNull(item.getTotalAmount()),
                                (left, right) -> left.add(right)))));

        List<DashboardOverviewResponse.Activity> activities = payments.stream().limit(10)
                .map(item -> new DashboardOverviewResponse.Activity(item.getId(), item.getReference(),
                        item.getMemberName(),
                        item.getAllocationType(), item.getAmount(), item.getPaymentMethod(), item.getStatus(),
                        item.getPaymentDate(), item.getDescription()))
                .toList();

        BigDecimal totalReceived = payments.stream().filter(item -> "COMPLETED".equals(item.getStatus()))
                .map(item -> zeroIfNull(item.getAmount())).reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        BigDecimal pendingAmount = payments.stream().filter(item -> "PENDING".equals(item.getStatus()))
                .map(item -> zeroIfNull(item.getAmount())).reduce(BigDecimal.ZERO, (left, right) -> left.add(right));

        long arrears = contributions.stream()
                .filter(item -> item.getStatus() != null && !"PAID".equals(item.getStatus())).count();
        long pendingLoans = loans.stream().filter(item -> {
            try {
                return List.of("PENDING", "UNDER_REVIEW")
                        .contains(item.getClass().getMethod("getStatus").invoke(item).toString());
            } catch (Exception ignored) {
                return false;
            }
        }).count();
        long unpaidFines = fines.stream()
                .filter(item -> item.getStatus() == FineStatus.UNPAID || item.getStatus() == FineStatus.PARTIAL)
                .count();

        return new DashboardOverviewResponse(
                new DashboardOverviewResponse.Summary(groupMemberRepository.countActiveMembersByGroupId(groupId),
                        contributionTotal,
                        shares.stream()
                                .mapToInt(item -> item.getType().equals("PURCHASE")
                                        || item.getType().equals("TRANSFER_IN") ? item.getQuantity()
                                                : -item.getQuantity())
                                .sum(),
                        shareCapital, totalOutstandingLoan(groupId)),
                contributionTrend, shareTrend,
                meetings.stream().limit(5)
                        .map(item -> new DashboardOverviewResponse.MeetingItem(item.getId(), item.getTitle(),
                                item.getMeetingDate(), item.getStartTime(), item.getLocation(), item.getStatus().name(),
                                item.getAgenda()))
                        .toList(),
                new DashboardOverviewResponse.Finance(totalReceived,
                        sumPayments(payments, "CASH"), sumPayments(payments, "BANK"),
                        sumPayments(payments, "MOBILE_MONEY"),
                        sumPayments(payments, "SOCIAL_FUND"), pendingAmount),
                activities,
                new DashboardOverviewResponse.Actions(pendingLoans, arrears, unpaidFines, meetings.size()));
    }

    private BigDecimal totalOutstandingLoan(Long groupId) {
        return entityManager.createQuery(
                "SELECT COALESCE(SUM(i.totalAmount - i.paidAmount), 0) FROM LoanInstallment i WHERE i.loan.groupMember.group.id = :groupId AND i.loan.status IN :statuses",
                BigDecimal.class)
                .setParameter("groupId", groupId)
                .setParameter("statuses", Arrays.asList(LoanStatus.DISBURSED, LoanStatus.ACTIVE)).getSingleResult();
    }

    private BigDecimal sumPayments(List<PaymentResponse> payments, String methodOrType) {
        return payments.stream().filter(item -> "COMPLETED".equals(item.getStatus()) &&
                (methodOrType.equals(item.getPaymentMethod()) || methodOrType.equals(item.getAllocationType())))
                .map(item -> zeroIfNull(item.getAmount())).reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<DashboardOverviewResponse.TrendPoint> trend(Map<YearMonth, BigDecimal> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new DashboardOverviewResponse.TrendPoint(entry.getKey().toString(), entry.getValue()))
                .toList();
    }
}
