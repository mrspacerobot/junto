package ch.junto.activitytype;

import ch.junto.activitytype.dto.ActivityTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity-types")
@Tag(name = "Activity Types", description = "Available activity types and their field definitions")
public class ActivityTypeController {

  private final ActivityTypeService activityTypeService;

  public ActivityTypeController(ActivityTypeService activityTypeService) {
    this.activityTypeService = activityTypeService;
  }

  @GetMapping
  @Operation(
      summary = "List activity types",
      description =
          "Returns all activity types with field definitions, summary field configuration, and default images")
  @ApiResponse(responseCode = "200", description = "List of activity types")
  public List<ActivityTypeResponse> list() {
    return activityTypeService.listTypes();
  }
}
