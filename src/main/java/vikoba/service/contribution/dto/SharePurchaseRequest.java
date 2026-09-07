package vikoba.service.contribution.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SharePurchaseRequest {
    private Long groupMemberId;
    private Integer quantity;
    private BigDecimal amount;
    private BigDecimal jamiiAmount;
    private String paymentMethod;
    private String reference;
}
