package vikoba.service.organization.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.contribution.entity.ShareTransaction;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.common.enums.LoanStatus;
import vikoba.service.loan.entity.Loan;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.organization.dto.DashboardStatistic;
import vikoba.service.organization.repository.GroupMemberRepository;

import java.math.BigDecimal;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final GroupMemberRepository groupMemberRepository;
    private final MemberContributionRepository memberContributionRepository;
    private final LoanRepository loanRepository;

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
}
