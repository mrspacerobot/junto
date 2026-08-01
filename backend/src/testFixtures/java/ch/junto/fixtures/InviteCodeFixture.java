package ch.junto.fixtures;

import ch.junto.invite.entity.InviteCode;
import ch.junto.user.entity.User;
import ch.junto.helpers.TimeTestHelper;

import java.util.UUID;

public final class InviteCodeFixture {

    private InviteCodeFixture() {}

    public static InviteCode valid(User creator) {
        InviteCode code = new InviteCode();
        code.setId(UUID.randomUUID());
        code.setCode("JUNTO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        code.setCreatedBy(creator);
        return code;
    }

    public static InviteCode used(User creator, User usedBy) {
        InviteCode code = valid(creator);
        code.setUsedBy(usedBy);
        code.setUsedAt(TimeTestHelper.hoursAgo(1));
        return code;
    }

    public static InviteCode revoked(User creator) {
        InviteCode code = valid(creator);
        code.setRevokedAt(TimeTestHelper.hoursAgo(1));
        return code;
    }
}
