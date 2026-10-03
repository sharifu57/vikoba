# System 360 (web)

The web route `/system` is the system administrator's workspace. It provides
system totals, all-group search, a paginated member directory with group filters,
leadership health, chairperson appointments, and audit history across groups.
OTP login sends super administrators directly here. Existing group menus also
show **System 360** to these accounts. The Flutter app has no new screens.

## Grant the first administrator

Deploy the backend and web changes together. The backend seeds the `SUPER_ADMIN`
role but never automatically grants it to a user. Use an existing ACTIVE account
with a phone number that can receive login OTPs. Group membership is not required.
Run the following against the correct database using your database administrator
connection (replace the example phone with the intended administrator):

```bash
psql "$DATABASE_URL" -v admin_phone='2557XXXXXXXX' -f docs/sql/grant-super-admin.sql
```

The script is idempotent. Confirm that its final SELECT returns the intended phone
and `SUPER_ADMIN`; no row means no active account matched. Sign in through the web
OTP flow and visit `/system`. Do not grant this role through ordinary group role
screens or to all group chairpersons. To revoke a particular administrator:

```sql
DELETE FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE phone = '2557XXXXXXXX')
  AND role_id = (SELECT id FROM roles WHERE name = 'SUPER_ADMIN');
```

The backend checks current database grants on every system request, so revocation
does not depend on changing a browser flag or waiting for a token to expire.

## Chairperson policy

- A newly created group starts with its creator/registrant as chairperson. Group
  creation rolls back if the initial membership/leadership assignment fails.
- Only System 360's super-admin endpoint can replace or appoint an existing
  group's chair. Both legacy `CHAIRPERSON` and `GROUP_CHAIRMAN` count as chairs;
  appointments use the canonical `GROUP_CHAIRMAN` role.
- Appointments require an active member of the selected group and a reason. The
  backend locks the group and selected membership, ends all previous chair roles,
  then inserts exactly one new active chair role in the same transaction. Other
  offices, permissions, and ordinary membership remain intact.
- A stale request receives HTTP 409 rather than overwriting a newer appointment.
  Refresh the group directory and reopen the appointment dialog before retrying.
- Group role editors cannot add/remove chair roles, including through direct API
  requests. A chair's membership cannot be suspended or exited until a replacement
  is appointed. These rules apply to the shared backend, including older clients.
- Existing missing, duplicate, or inactive chairs are flagged under **Leadership
  attention**. Resolve them deliberately using **Appoint chair**; the deployment
  does not silently choose an arbitrary member or rewrite existing leadership.
- Each appointment records the actor, former and new chair, and reason in the
  group's audit log, visible from System 360's audit history.

This feature uses the existing roles, memberships and audit tables; no new schema
migration is required. It does not give administrators implicit approval authority
inside every group's financial workflows. The system role and group offices remain
separate, following [OWASP authorization guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).
