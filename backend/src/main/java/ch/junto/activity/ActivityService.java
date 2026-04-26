package ch.junto.activity;

import ch.junto.activity.dto.*;
import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.enums.FieldType;
import ch.junto.core.geo.Haversine;
import ch.junto.participant.ParticipantService;
import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.core.exception.ApiException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.ConflictException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ch.junto.core.pagination.CursorPage;
import ch.junto.core.pagination.CursorPageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityTypeRegistry typeRegistry;
    private final ActivityFieldValidationService validationService;
    private final ParticipantService participantService;
    private final ObjectMapper objectMapper;

    public ActivityService(
        ActivityRepository activityRepository,
        ActivityTypeRegistry typeRegistry,
        ActivityFieldValidationService validationService,
        ParticipantService participantService,
        ObjectMapper objectMapper
    ) {
        this.activityRepository = activityRepository;
        this.typeRegistry = typeRegistry;
        this.validationService = validationService;
        this.participantService = participantService;
        this.objectMapper = objectMapper;
    }

    public ActivityDetailResponse create(ActivityCreateRequest request, User creator) {
        ActivityTypeDefinition type = typeRegistry.get(request.typeSlug());

        if (request.startsAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException("VALIDATION_ERROR", "startsAt must be in the future", 400);
        }
        if (request.signupDeadline() != null && !request.signupDeadline().isBefore(request.startsAt())) {
            throw new ApiException("VALIDATION_ERROR", "signupDeadline must be before startsAt", 400);
        }

        Activity activity = new Activity();
        activity.setCreator(creator);
        activity.setTypeSlug(type.getSlug());
        activity.setTitle(request.title());
        activity.setDescription(request.description());
        activity.setCity(request.city());
        activity.setLocationLat(request.locationLat());
        activity.setLocationLng(request.locationLng());
        activity.setStartsAt(request.startsAt());
        activity.setSignupDeadline(request.signupDeadline());
        activity.setMaxParticipants(request.maxParticipants());
        activity.setJoinPolicy(request.joinPolicy());

        if (request.customFields() != null) {
            activity.setCustomFields(request.customFields());
        }

        validationService.validate(type.getSlug(), activity.getCustomFields());

        activity = activityRepository.save(activity);
        participantService.addCreator(activity, creator);
        return mapToDetailResponse(activity);
    }

    @Transactional(readOnly = true)
    public ActivityDetailResponse getDetail(UUID id) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));
        return mapToDetailResponse(activity);
    }

    public ActivityDetailResponse update(UUID id, ActivityUpdateRequest request, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can update activity");
        }

        if (activity.getStatus() == ActivityStatus.COMPLETED) {
            throw new ConflictException("Completed activities cannot be edited");
        }

        if (request.signupDeadline() != null && !request.signupDeadline().isBefore(request.startsAt())) {
            throw new ApiException("VALIDATION_ERROR", "signupDeadline must be before startsAt", 400);
        }
        if (activity.getStartsAt().isAfter(OffsetDateTime.now())
                && request.startsAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException("VALIDATION_ERROR", "startsAt must be in the future", 400);
        }

        activity.setTitle(request.title());
        activity.setDescription(request.description());
        activity.setCity(request.city());
        activity.setLocationLat(request.locationLat());
        activity.setLocationLng(request.locationLng());
        activity.setStartsAt(request.startsAt());
        activity.setSignupDeadline(request.signupDeadline());
        if (request.maxParticipants() != null) {
            long approvedCount = participantService.countApproved(id);
            if (request.maxParticipants() < approvedCount) {
                throw new ConflictException(
                    "maxParticipants cannot be reduced below current approved participant count (" + approvedCount + ")");
            }
            activity.setMaxParticipants(request.maxParticipants());
        }
        if (request.joinPolicy() != null) {
            activity.setJoinPolicy(request.joinPolicy());
        }

        if (request.customFields() != null) {
            activity.getCustomFields().putAll(request.customFields());
        }

        validationService.validate(activity.getTypeSlug(), activity.getCustomFields());

        activity = activityRepository.save(activity);
        return mapToDetailResponse(activity);
    }

    public void cancel(UUID id, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can cancel activity");
        }

        if (activity.getStatus() != ActivityStatus.ACTIVE) {
            throw new ConflictException("Activity is not active");
        }

        activity.setStatus(ActivityStatus.CANCELLED);
        activityRepository.save(activity);
    }

    public void complete(UUID id, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can complete activity");
        }

        if (activity.getStatus() != ActivityStatus.ACTIVE) {
            throw new ConflictException("Activity is not active");
        }

        activity.setStatus(ActivityStatus.COMPLETED);
        activityRepository.save(activity);
    }

    public void reactivate(UUID id, User user) {
        Activity activity = activityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (!activity.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Only creator can reactivate activity");
        }

        if (activity.getStatus() != ActivityStatus.CANCELLED) {
            throw new ConflictException("Activity is not cancelled");
        }

        activity.setStatus(ActivityStatus.ACTIVE);
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public CursorPage<ActivityListItemResponse> list(ActivityFilterParams filters, CursorPageable pageable, User currentUser) {
        // Resolve search origin: query params → stored user location → null
        Double searchLat = filters.getLat();
        Double searchLng = filters.getLng();

        if (searchLat == null && searchLng == null && currentUser != null) {
            searchLat = currentUser.getLocationLat();
            searchLng = currentUser.getLocationLng();
        }

        // Validate: both or neither
        if ((searchLat == null) != (searchLng == null)) {
            throw new ApiException("VALIDATION_ERROR", "lat and lng must both be provided or both absent", 400);
        }

        if (searchLat != null) {
            if (searchLat < -90 || searchLat > 90) {
                throw new ApiException("VALIDATION_ERROR", "lat must be between -90 and 90", 400);
            }
            if (searchLng < -180 || searchLng > 180) {
                throw new ApiException("VALIDATION_ERROR", "lng must be between -180 and 180", 400);
            }
        }

        double radiusKm = 25.0;
        if (filters.getRadiusKm() != null) {
            if (filters.getRadiusKm() <= 0 || filters.getRadiusKm() > 200) {
                throw new ApiException("VALIDATION_ERROR", "radiusKm must be between 0 (exclusive) and 200", 400);
            }
            radiusKm = filters.getRadiusKm();
        }

        final Double finalLat = searchLat;
        final Double finalLng = searchLng;
        final double radiusMeters = radiusKm * 1000.0;

        Specification<Activity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filters.getCity() != null && !filters.getCity().isBlank()) {
                predicates.add(cb.equal(root.get("city"), filters.getCity()));
            }
            if (filters.getTypeSlug() != null && !filters.getTypeSlug().isBlank()) {
                predicates.add(cb.equal(root.get("typeSlug"), filters.getTypeSlug()));
            }
            if (filters.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filters.getStatus()));
            } else {
                predicates.add(cb.equal(root.get("status"), ActivityStatus.ACTIVE));
            }
            if (filters.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), filters.getFromDate()));
            }
            if (filters.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), filters.getToDate()));
            }

            // Custom field containment filter.
            // Requires typeSlug so field names can be validated against the type registry.
            // Uses PostgreSQL @> containment: custom_fields @> '{"style":"Bouldering"}'::jsonb
            // The GIN index (idx_activities_custom_fields_gin) serves this operator.
            // v1 scope: SELECT fields only. BOOLEAN fields store as JSON booleans but filter params
            // arrive as strings — mixing types causes silent @> mismatches. Expand in v2.
            if (filters.getCustomFields() != null && !filters.getCustomFields().isEmpty()) {
                if (filters.getTypeSlug() == null || filters.getTypeSlug().isBlank()) {
                    throw new ApiException("VALIDATION_ERROR",
                        "typeSlug is required when filtering by custom fields", 400);
                }
                ActivityTypeDefinition type = typeRegistry.get(filters.getTypeSlug());
                Map<String, CustomFieldDefinition> fieldDefsByName = type.getFieldDefinitions().stream()
                    .collect(Collectors.toMap(CustomFieldDefinition::name, d -> d));

                for (String key : filters.getCustomFields().keySet()) {
                    CustomFieldDefinition def = fieldDefsByName.get(key);
                    if (def == null) {
                        throw new ApiException("VALIDATION_ERROR",
                            "Unknown filter field: " + key, 400);
                    }
                    if (def.type() != FieldType.SELECT) {
                        throw new ApiException("VALIDATION_ERROR",
                            "Filtering on field '" + key + "' is not supported (only SELECT fields in v1)", 400);
                    }
                }

                try {
                    String jsonFilter = objectMapper.writeValueAsString(filters.getCustomFields());
                    predicates.add(cb.isTrue(
                        cb.function("jsonb_contains_op", Boolean.class,
                            root.get("customFields"),
                            cb.literal(jsonFilter)
                        )
                    ));
                } catch (JacksonException e) {
                    throw new RuntimeException("Failed to serialize custom field filter", e);
                }
            }

            // Note: jsonb_extract_path_text doesn't leverage GIN indexes.
            // If this becomes a bottleneck, switch to @> containment or add expression indexes.
            // See jsonb-custom-field-filtering.md "Not in scope (v2)"

            // Spatial filter: only activities within radius of search origin
            if (finalLat != null) {
                String pointWkt = "SRID=4326;POINT(" + finalLng + " " + finalLat + ")";
                predicates.add(cb.isTrue(
                    cb.function("st_dwithin", Boolean.class,
                        root.get("location"),
                        cb.literal(pointWkt),
                        cb.literal(radiusMeters)
                    )
                ));
                // Exclude activities without a location
                predicates.add(cb.isNotNull(root.get("location")));
            }

            if (pageable.hasCursor()) {
                String decoded = new String(Base64.getDecoder().decode(pageable.getCursor()));
                String[] parts = decoded.split("\\|");
                OffsetDateTime cursorTime = OffsetDateTime.parse(parts[0]);
                UUID cursorId = UUID.fromString(parts[1]);

                predicates.add(cb.or(
                    cb.greaterThan(root.get("startsAt"), cursorTime),
                    cb.and(
                        cb.equal(root.get("startsAt"), cursorTime),
                        cb.greaterThan(root.get("id"), cursorId)
                    )
                ));
            }

            query.orderBy(cb.asc(root.get("startsAt")), cb.asc(root.get("id")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable limitedPage = PageRequest.of(0, pageable.getLimit() + 1);
        List<Activity> activities = activityRepository.findAll(spec, limitedPage).getContent();

        boolean hasNext = activities.size() > pageable.getLimit();
        List<Activity> resultList = hasNext ? activities.subList(0, pageable.getLimit()) : activities;

        String nextCursor = null;
        if (hasNext) {
            Activity last = resultList.get(resultList.size() - 1);
            String cursorRaw = last.getStartsAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "|" + last.getId();
            nextCursor = Base64.getEncoder().encodeToString(cursorRaw.getBytes());
        }

        final Double distLat = finalLat;
        final Double distLng = finalLng;
        List<ActivityListItemResponse> dtos = resultList.stream()
            .map(a -> {
                Double distance = null;
                if (distLat != null && a.getLocationLat() != null && a.getLocationLng() != null) {
                    distance = Haversine.distanceKm(distLat, distLng, a.getLocationLat(), a.getLocationLng());
                }
                return mapToListItemResponse(a, distance);
            })
            .collect(Collectors.toList());

        return new CursorPage<>(dtos, nextCursor);
    }

    @Transactional
    public int autoCompleteStartedActivities() {
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(4);
        List<Activity> started = activityRepository.findAll((root, query, cb) ->
            cb.and(
                cb.equal(root.get("status"), ActivityStatus.ACTIVE),
                cb.lessThan(root.get("startsAt"), threshold)
            )
        );

        for (Activity a : started) {
            a.setStatus(ActivityStatus.COMPLETED);
        }
        activityRepository.saveAll(started);
        return started.size();
    }

    private String resolveImageUrl(Activity a) {
        if (a.getImageUrl() != null) {
            return a.getImageUrl();
        }
        return typeRegistry.get(a.getTypeSlug()).getDefaultImageUrl();
    }

    private List<ActivitySummaryField> buildSummary(Activity a) {
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());
        Map<String, CustomFieldDefinition> fieldDefs = type.getFieldDefinitions().stream()
            .collect(Collectors.toMap(CustomFieldDefinition::name, d -> d));

        List<ActivitySummaryField> summary = new ArrayList<>();
        for (String fieldName : type.getSummaryFields()) {
            CustomFieldDefinition def = fieldDefs.get(fieldName);
            Object value = a.getCustomFields().get(fieldName);
            if (value == null) continue;

            String displayValue;
            if (def.type() == FieldType.NUMBER_WITH_UNIT && def.unit() != null) {
                displayValue = value + " " + def.unit().name().toLowerCase();
            } else {
                displayValue = value.toString();
            }

            summary.add(new ActivitySummaryField(fieldName, def.label(), value, displayValue));
        }
        return summary;
    }

    private ActivityListItemResponse mapToListItemResponse(Activity a) {
        return mapToListItemResponse(a, null);
    }

    private ActivityListItemResponse mapToListItemResponse(Activity a, Double distance) {
        long currentParticipants = participantService.countApproved(a.getId());
        List<String> participantAvatars = participantService.getTopParticipantAvatars(a.getId(), 3);
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());

        return new ActivityListItemResponse(
            a.getId(),
            a.getTitle(),
            type.getName(),
            type.getSlug(),
            a.getCity(),
            a.getStartsAt(),
            a.getMaxParticipants(),
            (int) currentParticipants,
            a.getStatus(),
            a.getCreator().getDisplayName(),
            a.getCreator().getAvatarUrl(),
            a.getCustomFields(),
            resolveImageUrl(a),
            participantAvatars,
            buildSummary(a),
            distance
        );
    }

    private ActivityDetailResponse mapToDetailResponse(Activity a) {
        long currentParticipants = participantService.countApproved(a.getId());
        ActivityTypeDefinition type = typeRegistry.get(a.getTypeSlug());

        OffsetDateTime effectiveDeadline = a.getSignupDeadline() != null ? a.getSignupDeadline() : a.getStartsAt();
        boolean isJoinable = a.getStatus() == ActivityStatus.ACTIVE &&
                           OffsetDateTime.now().isBefore(effectiveDeadline) &&
                           currentParticipants < a.getMaxParticipants();

        return new ActivityDetailResponse(
            a.getId(),
            a.getTitle(),
            a.getDescription(),
            type.getName(),
            type.getSlug(),
            a.getCity(),
            a.getLocationLat(),
            a.getLocationLng(),
            a.getStartsAt(),
            a.getSignupDeadline(),
            a.getMaxParticipants(),
            (int) currentParticipants,
            a.getJoinPolicy(),
            a.getStatus(),
            a.getCustomFields(),
            resolveImageUrl(a),
            a.getCreator().getId(),
            a.getCreator().getDisplayName(),
            a.getCreator().getAvatarUrl(),
            isJoinable,
            effectiveDeadline
        );
    }
}
