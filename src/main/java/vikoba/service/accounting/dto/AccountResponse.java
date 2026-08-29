package vikoba.service.accounting.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class AccountResponse { private Long id; private String code; private String name; private String type; private boolean active; private java.math.BigDecimal debit; private java.math.BigDecimal credit; private java.math.BigDecimal balance; }
