package vikoba.service.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class DashboardStatistic {
    private Long totalGroupMembers;
    private BigDecimal totalGroupContributionAmount;
    private BigDecimal totalShares;
    private BigDecimal totalGroupOutStandingLoan;
}
