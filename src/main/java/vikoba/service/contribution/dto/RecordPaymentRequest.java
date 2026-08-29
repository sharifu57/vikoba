package vikoba.service.contribution.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RecordPaymentRequest {
    private Long groupMemberId;
    private BigDecimal amount;
    private String paymentMethod;
    private String reference;
    private String externalReference;
    private String status;
    private String allocationType;
    private Long allocationReferenceId;
    private String description;
}
