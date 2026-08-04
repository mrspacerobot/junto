package ch.junto.activitytype;

import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.dto.ActivityTypeResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ActivityTypeService {

  private final ActivityTypeRegistry registry;

  public ActivityTypeService(ActivityTypeRegistry registry) {
    this.registry = registry;
  }

  public List<ActivityTypeResponse> listTypes() {
    return registry.listAll().stream().map(this::mapToResponse).collect(Collectors.toList());
  }

  private ActivityTypeResponse mapToResponse(ActivityTypeDefinition type) {
    return new ActivityTypeResponse(
        type.getSlug(),
        type.getName(),
        type.getIconName(),
        type.getDefaultImageUrl(),
        type.getFieldDefinitions(),
        type.getSummaryFields());
  }
}
