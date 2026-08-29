package vikoba.service.expense.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseCategoryRequest {
    private String name;
    private String description;
    private Boolean active;
}
