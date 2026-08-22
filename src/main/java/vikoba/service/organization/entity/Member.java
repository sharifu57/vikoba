package vikoba.service.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.Gender;
import vikoba.service.common.enums.MemberStatus;

import java.time.LocalDate;


@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_number",
                        columnNames = "member_number"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member extends BaseEntity {

    @Column(
            name = "member_number",
            nullable = false,
            unique = true,
            length = 50
    )
    private String memberNumber;

    @Column(name = "national_id", length = 100)
    private String nationalId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 150)
    private String occupation;

    @Column(name = "next_of_kin_name", length = 200)
    private String nextOfKinName;

    @Column(name = "next_of_kin_phone", length = 30)
    private String nextOfKinPhone;

    @Column(name = "next_of_kin_relationship", length = 100)
    private String nextOfKinRelationship;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;
}
