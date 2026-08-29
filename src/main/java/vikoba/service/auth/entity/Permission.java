package vikoba.service.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
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

    @Column(nullable = false, unique = true, length = 100)
    private String name;
    private String description;
}
