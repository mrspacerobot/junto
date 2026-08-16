package ch.junto.participant;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.participant.enums.ParticipantStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ParticipantStatus")
class ParticipantStatusTest {

  @Test
  @DisplayName("should not contain CANCELLED value")
  void shouldNotContainCancelled() {
    List<String> values = Arrays.stream(ParticipantStatus.values()).map(Enum::name).toList();
    assertThat(values).doesNotContain("CANCELLED");
  }
}
