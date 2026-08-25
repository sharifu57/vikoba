package vikoba.service.accounting.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AccountRequest { private String code; private String name; private String type; private Boolean active; }
