package vikoba.service.common.enums;

public enum GroupRole {
    /** Full group administration. This is the only group role that bypasses permissions. */
    GROUP_ADMIN,

    /** Mwenyekiti: leads governance and can be assigned approvals through workflows. */
    GROUP_CHAIRMAN,

    CHAIRPERSON,

    VICE_CHAIRPERSON,

    SECRETARY,

    TREASURER,

    ACCOUNTANT,

    LOAN_OFFICER,

    AUDITOR,

    MEMBER
}
