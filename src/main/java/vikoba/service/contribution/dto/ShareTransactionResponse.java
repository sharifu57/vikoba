package vikoba.service.contribution.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ShareTransactionResponse {
    private Long id;
    private Long groupMemberId;
    private String memberName;
    private String membershipNumber;
    private String type;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String reference;
    private LocalDateTime transactionDate;
}
