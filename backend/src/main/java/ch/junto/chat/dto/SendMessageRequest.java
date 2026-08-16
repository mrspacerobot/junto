package ch.junto.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @NotBlank(message = "Content must not be blank")
        @Size(max = 2000, message = "Content must not exceed 2000 characters")
        String content) {}
