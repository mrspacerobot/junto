package ch.junto.activity;

import ch.junto.activity.dto.*;
import ch.junto.auth.CurrentUser;
import ch.junto.user.entity.User;
import ch.junto.core.pagination.CursorPage;
import ch.junto.core.pagination.CursorPageable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
@Tag(name = "Activities", description = "Create, list, update, and manage activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    @Operation(summary = "Create activity", description = "Create a new activity. Custom fields are validated against the activity type's field definitions.")
    @ApiResponse(responseCode = "200", description = "Activity created")
    @ApiResponse(responseCode = "400", description = "Invalid request or custom field validation failed")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    public ActivityDetailResponse create(@RequestBody @Valid ActivityCreateRequest request, @CurrentUser User user) {
        return activityService.create(request, user);
    }

    @GetMapping
    @Operation(summary = "List activities", description = "List activities with optional filters and cursor-based pagination")
    @ApiResponse(responseCode = "200", description = "Page of activities")
    public CursorPage<ActivityListItemResponse> list(ActivityFilterParams filters, CursorPageable pageable, @CurrentUser User user) {
        return activityService.list(filters, pageable, user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get activity detail")
    @ApiResponse(responseCode = "200", description = "Activity details")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public ActivityDetailResponse getDetail(@PathVariable UUID id) {
        return activityService.getDetail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update activity", description = "Only the creator can update. Type cannot be changed.")
    @ApiResponse(responseCode = "200", description = "Activity updated")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public ActivityDetailResponse update(
        @PathVariable UUID id,
        @RequestBody @Valid ActivityUpdateRequest request,
        @CurrentUser User user
    ) {
        return activityService.update(id, request, user);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel activity", description = "Only the creator can cancel.")
    @ApiResponse(responseCode = "200", description = "Activity cancelled")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public void cancel(@PathVariable UUID id, @CurrentUser User user) {
        activityService.cancel(id, user);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete activity", description = "Only the creator can complete.")
    @ApiResponse(responseCode = "200", description = "Activity completed")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    public void complete(@PathVariable UUID id, @CurrentUser User user) {
        activityService.complete(id, user);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate activity", description = "Only the creator can reactivate a cancelled activity.")
    @ApiResponse(responseCode = "200", description = "Activity reactivated")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the activity creator")
    @ApiResponse(responseCode = "404", description = "Activity not found")
    @ApiResponse(responseCode = "409", description = "Activity is not cancelled")
    public void reactivate(@PathVariable UUID id, @CurrentUser User user) {
        activityService.reactivate(id, user);
    }
}
