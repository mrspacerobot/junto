package ch.junto.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ch.junto.activity.dto.ActivityCreateRequest;
import ch.junto.activity.dto.ActivityDetailResponse;
import ch.junto.activity.dto.ActivityUpdateRequest;
import ch.junto.activity.entity.Activity;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.activitytype.ActivityTypeRegistry;
import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

  @Mock private ActivityRepository activityRepository;
  @Mock private ActivityFieldValidationService validationService;
  @Mock private ch.junto.participant.ParticipantService participantService;

  private ActivityTypeRegistry typeRegistry;
  private ActivityService activityService;

  @BeforeEach
  void setUp() {
    typeRegistry = new ActivityTypeRegistry(List.of(new ClimbingType()));
    activityService =
        new ActivityService(
            activityRepository,
            typeRegistry,
            validationService,
            participantService,
            new ObjectMapper());
  }

  @Test
  void create_validRequest_savesAndReturnsDetail() {
    User creator = UserFixture.defaultUser();
    ActivityCreateRequest request =
        new ActivityCreateRequest(
            "Test Title",
            "Test Desc",
            "climbing",
            "Boulder",
            null,
            null,
            OffsetDateTime.now().plusDays(1),
            null,
            10,
            JoinPolicy.OPEN,
            null);

    when(activityRepository.save(any(Activity.class)))
        .thenAnswer(
            i -> {
              Activity a = i.getArgument(0);
              a.setId(UUID.randomUUID());
              return a;
            });

    ActivityDetailResponse response = activityService.create(request, creator);

    assertThat(response.title()).isEqualTo("Test Title");
    assertThat(response.activityTypeSlug()).isEqualTo("climbing");
    assertThat(response.imageUrl()).isNotBlank();
    verify(validationService).validate(eq("climbing"), any());
    verify(activityRepository).save(any());
  }

  @Test
  void create_unknownType_throwsNotFoundException() {
    User creator = UserFixture.defaultUser();
    ActivityCreateRequest request =
        new ActivityCreateRequest(
            "Test Title",
            "Test Desc",
            "unknown",
            "Boulder",
            null,
            null,
            OffsetDateTime.now().plusDays(1),
            null,
            10,
            JoinPolicy.OPEN,
            null);

    assertThatThrownBy(() -> activityService.create(request, creator))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void create_imageUrlFallsBackToTypeDefault() {
    User creator = UserFixture.defaultUser();
    ActivityCreateRequest request =
        new ActivityCreateRequest(
            "Test",
            "Desc",
            "climbing",
            "City",
            null,
            null,
            OffsetDateTime.now().plusDays(1),
            null,
            10,
            JoinPolicy.OPEN,
            null);

    when(activityRepository.save(any(Activity.class)))
        .thenAnswer(
            i -> {
              Activity a = i.getArgument(0);
              a.setId(UUID.randomUUID());
              return a;
            });

    ActivityDetailResponse response = activityService.create(request, creator);
    assertThat(response.imageUrl()).isEqualTo(new ClimbingType().getDefaultImageUrl());
  }

  @Test
  void getDetail_found_returnsDetail() {
    User creator = UserFixture.defaultUser();
    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

    when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

    ActivityDetailResponse response = activityService.getDetail(activity.getId());

    assertThat(response.id()).isEqualTo(activity.getId());
    assertThat(response.title()).isEqualTo(activity.getTitle());
  }

  @Test
  void getDetail_notFound_throwsNotFoundException() {
    UUID id = UUID.randomUUID();
    when(activityRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> activityService.getDetail(id)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void update_byCreator_savesAndReturnsDetail() {
    User creator = UserFixture.defaultUser();
    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");
    ActivityUpdateRequest request =
        new ActivityUpdateRequest(
            "Updated Title",
            "New Desc",
            "New City",
            null,
            null,
            OffsetDateTime.now().plusDays(2),
            null,
            null,
            null,
            null);

    when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
    when(activityRepository.save(any(Activity.class))).thenReturn(activity);

    ActivityDetailResponse response = activityService.update(activity.getId(), request, creator);

    assertThat(response.title()).isEqualTo("Updated Title");
    verify(activityRepository).save(activity);
  }

  @Test
  void update_notCreator_throwsForbiddenException() {
    User creator = UserFixture.defaultUser();
    User otherUser = UserFixture.defaultUser();
    otherUser.setId(UUID.randomUUID());

    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");
    ActivityUpdateRequest request =
        new ActivityUpdateRequest(
            "Updated Title",
            "New Desc",
            "New City",
            null,
            null,
            OffsetDateTime.now().plusDays(2),
            null,
            null,
            null,
            null);

    when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));

    assertThatThrownBy(() -> activityService.update(activity.getId(), request, otherUser))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void update_notFound_throwsNotFoundException() {
    UUID id = UUID.randomUUID();
    User user = UserFixture.defaultUser();
    ActivityUpdateRequest request =
        new ActivityUpdateRequest(
            "Updated Title",
            "New Desc",
            "New City",
            null,
            null,
            OffsetDateTime.now().plusDays(2),
            null,
            null,
            null,
            null);

    when(activityRepository.findByIdWithLock(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> activityService.update(id, request, user))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void cancel_byCreator_setsStatusCancelled() {
    User creator = UserFixture.defaultUser();
    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

    when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
    when(activityRepository.save(any(Activity.class))).thenReturn(activity);

    activityService.cancel(activity.getId(), creator);

    assertThat(activity.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
    verify(activityRepository).save(activity);
  }

  @Test
  void cancel_notCreator_throwsForbiddenException() {
    User creator = UserFixture.defaultUser();
    User otherUser = UserFixture.defaultUser();
    otherUser.setId(UUID.randomUUID());
    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

    when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));

    assertThatThrownBy(() -> activityService.cancel(activity.getId(), otherUser))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void cancel_notFound_throwsNotFoundException() {
    UUID id = UUID.randomUUID();
    when(activityRepository.findByIdWithLock(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> activityService.cancel(id, UserFixture.defaultUser()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void complete_byCreator_setsStatusCompleted() {
    User creator = UserFixture.defaultUser();
    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

    when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
    when(activityRepository.save(any(Activity.class))).thenReturn(activity);

    activityService.complete(activity.getId(), creator);

    assertThat(activity.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    verify(activityRepository).save(activity);
  }

  @Test
  void complete_notCreator_throwsForbiddenException() {
    User creator = UserFixture.defaultUser();
    User otherUser = UserFixture.defaultUser();
    otherUser.setId(UUID.randomUUID());
    Activity activity = ActivityFixture.defaultActivity(creator, "climbing");

    when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));

    assertThatThrownBy(() -> activityService.complete(activity.getId(), otherUser))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void complete_notFound_throwsNotFoundException() {
    UUID id = UUID.randomUUID();
    when(activityRepository.findByIdWithLock(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> activityService.complete(id, UserFixture.defaultUser()))
        .isInstanceOf(NotFoundException.class);
  }
}
