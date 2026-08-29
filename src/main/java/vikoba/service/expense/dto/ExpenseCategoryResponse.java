package vikoba.service.expense.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpenseCategoryResponse {
    private Long id;
    private Long groupId;
    private String name;
    private String description;
    private boolean active;
}
