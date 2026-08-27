package vikoba.service.social.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SocialFundTypeInput {
    private String code;
    private String name;
    private String description;
    private BigDecimal defaultContribution;
    private Boolean mandatory;
}
