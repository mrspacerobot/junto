package ch.junto.invite;

import ch.junto.auth.CurrentUser;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.entity.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    public List<InviteCode> list(@CurrentUser User user) {
        return inviteService.listUserCodes(user.getId());
    }

    @PostMapping
    public InviteCode create(@CurrentUser User user) {
        return inviteService.generateCode(user.getId());
    }
}
