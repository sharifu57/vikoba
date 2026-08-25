package vikoba.service.contribution.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {
    private Long id;
    private Long groupMemberId;
    private String memberName;
    private String membershipNumber;
    private String reference;
    private String externalReference;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String allocationType;
    private Long allocationReferenceId;
    private String description;
    private LocalDateTime paymentDate;
}
