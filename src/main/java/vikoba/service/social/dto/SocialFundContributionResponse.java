package vikoba.service.social.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialFundContributionResponse {
    private Long id;

    private Long groupMemberId;

    private Long fundTypeId;

    private BigDecimal amount;

    private LocalDate contributionDate;

    private String reference;
}
