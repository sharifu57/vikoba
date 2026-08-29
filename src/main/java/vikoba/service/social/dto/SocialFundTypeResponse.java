package vikoba.service.social.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SocialFundTypeResponse {
    private final Long id;
    private final String code;
    private final String name;
    private final String description;
    private final BigDecimal defaultContribution;
    private final boolean mandatory;
    private final boolean active;
}
