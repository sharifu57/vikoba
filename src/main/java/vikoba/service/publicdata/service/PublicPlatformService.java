package vikoba.service.publicdata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.contribution.repository.ShareTransactionRepository;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.publicdata.dto.PublicPlatformSummaryResponse;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicPlatformService {
    private final VikobaGroupRepository groups;
    private final GroupMemberRepository members;
    private final MemberContributionRepository contributions;
    private final ShareTransactionRepository shares;
    private final LoanRepository loans;

    public PublicPlatformSummaryResponse summary() {
        BigDecimal totalContributions = zero(contributions.sumPaidAmount());
        BigDecimal totalShares = zero(shares.sumPurchaseAmount());
        BigDecimal totalLoanAmount = zero(loans.sumIssuedAmount());
        long totalTransactions = contributions.count() + shares.count() + loans.count();

        return new PublicPlatformSummaryResponse(
                new PublicPlatformSummaryResponse.PublicStatistics(
                        groups.count(), groups.countActiveGroups(), members.countActiveMembers(),
                        totalContributions, totalShares, loans.countIssued(), totalLoanAmount, totalTransactions),
                List.of(), List.of());
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
