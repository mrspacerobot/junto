package ch.junto.chat.event;

import ch.junto.chat.dto.ChatMessageResponse;

public record ChatMessageCommittedEvent(ChatMessageResponse message) {}
