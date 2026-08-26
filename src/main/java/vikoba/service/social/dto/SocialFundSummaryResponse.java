package vikoba.service.social.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SocialFundSummaryResponse {
    private BigDecimal totalContributions;
    private BigDecimal totalApproved;
    private BigDecimal totalPaid;
    private BigDecimal pendingRequests;
    private BigDecimal availableBalance;
    private long requestCount;
    private long pendingCount;
}
