package ch.junto.activitytype;

import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.activitytype.definition.CyclingType;
import ch.junto.activitytype.dto.ActivityTypeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTypeServiceTest {

    @Test
    void listTypes_returnsAllRegisteredTypes() {
        var registry = new ActivityTypeRegistry(List.of(new ClimbingType(), new CyclingType()));
        var service = new ActivityTypeService(registry);

        List<ActivityTypeResponse> result = service.listTypes();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ActivityTypeResponse::slug)
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
