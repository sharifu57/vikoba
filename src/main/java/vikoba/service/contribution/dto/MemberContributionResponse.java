package vikoba.service.contribution.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberContributionResponse {
    private Long id;

    private Long groupMemberId;

    private Long contributionPeriodId;

    private BigDecimal expectedAmount;

    private BigDecimal paidAmount;

    private BigDecimal balance;

    private String status;

    private LocalDateTime paidAt;
}
