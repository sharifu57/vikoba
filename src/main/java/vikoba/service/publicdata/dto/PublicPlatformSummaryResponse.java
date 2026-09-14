package vikoba.service.publicdata.dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicPlatformSummaryResponse(
        PublicStatistics statistics,
        List<PublicGrowthPoint> growth,
        List<PublicFinancialPoint> financialActivity) {

    public record PublicStatistics(
            long totalGroups,
            long activeGroups,
            long totalMembers,
            BigDecimal totalContributions,
            BigDecimal totalShares,
            long totalLoansIssued,
            BigDecimal totalLoanAmount,
            long totalTransactions) {
    }

    public record PublicGrowthPoint(String label, long groups, long members) {
    }

    public record PublicFinancialPoint(
            String label,
            BigDecimal contributions,
            BigDecimal shares,
            BigDecimal loans) {
    }
}
