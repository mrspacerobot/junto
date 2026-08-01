package ch.junto.message;

import ch.junto.auth.CurrentUser;
import ch.junto.message.dto.MessageThreadResponse;
import ch.junto.user.entity.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageThreadService threadService;

    public MessageController(MessageThreadService threadService) {
        this.threadService = threadService;
    }

    @GetMapping("/threads")
    public List<MessageThreadResponse> getThreads(@CurrentUser User user) {
        return threadService.getThreads(user);
    }
}
