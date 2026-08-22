package vikoba.service.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends BaseEntity {

    private String name;
    private String description;
}
