package vikoba.service.contribution.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ShareSummaryResponse {
    private BigDecimal unitPrice;
    private Integer totalShares;
    private BigDecimal totalCapital;
    private Integer holdersCount;
    private Integer totalMembers;
    private Integer maximumSharesPerMember;
}
