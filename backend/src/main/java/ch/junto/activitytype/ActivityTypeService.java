package ch.junto.activitytype;

import ch.junto.activity.validation.ActivityFieldValidator;
import ch.junto.activitytype.dto.ActivityTypeResponse;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.entity.ActivityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ActivityTypeService {

    private final ActivityTypeRepository activityTypeRepository;
    private final Map<String, ActivityFieldValidator> validators;

    public ActivityTypeService(ActivityTypeRepository activityTypeRepository, List<ActivityFieldValidator> validatorList) {
        this.activityTypeRepository = activityTypeRepository;
        this.validators = validatorList.stream()
            .collect(Collectors.toMap(ActivityFieldValidator::getActivityTypeSlug, Function.identity()));
    }

    public List<ActivityTypeResponse> listActiveTypes() {
        return activityTypeRepository.findAllByActiveTrue().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private ActivityTypeResponse mapToResponse(ActivityType type) {
        ActivityFieldValidator validator = validators.get(type.getSlug());
        List<CustomFieldDefinition> customFields = validator != null ? validator.getFieldDefinitions() : Collections.emptyList();
        
        return new ActivityTypeResponse(
            type.getId(),
            type.getSlug(),
            type.getName(),
            type.getIconName(),
            customFields
        );
    }
}
