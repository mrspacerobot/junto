package ch.junto.activitytype;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.activitytype.definition.CyclingType;
import ch.junto.activitytype.dto.ActivityTypeResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityTypeServiceTest {

  @Test
  void listTypes_returnsAllRegisteredTypes() {
    var registry = new ActivityTypeRegistry(List.of(new ClimbingType(), new CyclingType()));
    var service = new ActivityTypeService(registry);

    List<ActivityTypeResponse> result = service.listTypes();

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(ActivityTypeResponse::slug)
        .containsExactlyInAnyOrder("climbing", "cycling");
  }

  @Test
  void listTypes_includesFieldDefinitionsAndSummaryFields() {
    var registry = new ActivityTypeRegistry(List.of(new ClimbingType()));
    var service = new ActivityTypeService(registry);

    List<ActivityTypeResponse> result = service.listTypes();

    assertThat(result).hasSize(1);
    var climbing = result.get(0);
    assertThat(climbing.customFields()).hasSize(5);
    assertThat(climbing.summaryFields()).containsExactly("style", "grade");
    assertThat(climbing.defaultImageUrl()).isNotBlank();
  }
}
