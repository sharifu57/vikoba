package vikoba.service.accounting.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Builder
public class LedgerLineResponse { private Long id; private Long transactionId; private LocalDateTime transactionDate; private String reference; private String description; private Long accountId; private String accountCode; private String accountName; private BigDecimal debit; private BigDecimal credit; private BigDecimal balance; }
