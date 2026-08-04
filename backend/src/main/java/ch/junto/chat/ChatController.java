package ch.junto.chat;

import ch.junto.auth.CurrentUser;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.chat.dto.SendMessageRequest;
import ch.junto.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities/{activityId}/messages")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping
  public ChatMessageResponse send(
      @PathVariable UUID activityId,
      @RequestBody @Valid SendMessageRequest request,
      @CurrentUser User user) {
    return chatService.sendMessage(activityId, request.content(), user);
  }

  @GetMapping
  public List<ChatMessageResponse> list(
      @PathVariable UUID activityId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) UUID before,
      @CurrentUser User user) {
    return chatService.getMessages(activityId, user, limit, before);
  }
}
