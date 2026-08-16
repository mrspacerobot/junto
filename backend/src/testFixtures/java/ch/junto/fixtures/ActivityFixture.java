package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.entity.CustomFields;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ActivityFixture {

  /** Returns an activity WITH a generated UUID (for Unit Tests) */
  public static Activity defaultActivity(User creator, String typeSlug) {
    Activity activity = newActivity(creator, typeSlug);
    activity.setId(UUID.randomUUID());
    return activity;
  }

  /** Returns an activity WITHOUT a UUID (for Integration Tests / DatabaseHelper) */
  public static Activity newActivity(User creator, String typeSlug) {
    Activity activity = new Activity();
    activity.setCreator(creator);
    activity.setTypeSlug(typeSlug);
    activity.setTitle("Test Activity");
    activity.setDescription("Test Description");
    activity.setCity("Boulder");
    activity.setStartsAt(OffsetDateTime.now().plusDays(1));
    activity.setMaxParticipants(10);
    activity.setJoinPolicy(JoinPolicy.OPEN);
    activity.setStatus(ActivityStatus.ACTIVE);

    CustomFields fields = new CustomFields();
    if ("climbing".equals(typeSlug)) {
      fields.put("style", "Bouldering");
      fields.put("environment", "Indoor");
      fields.put("experienceLevel", "Beginner");
    }
    activity.setCustomFields(fields);

    return activity;
  }

  public static Activity newClimbingActivity(User creator) {
    return newActivity(creator, "climbing");
  }

  public static Activity newCyclingActivity(User creator) {
    return newActivity(creator, "cycling");
  }

  public static Activity newActivityAt(
      User creator,
      String typeSlug,
      String title,
      double lat,
      double lng,
      OffsetDateTime startsAt) {
    Activity activity = newActivity(creator, typeSlug);
    activity.setTitle(title);
    activity.setLocationLat(lat);
    activity.setLocationLng(lng);
    activity.setStartsAt(startsAt);
    return activity;
  }

  public static Activity newActivityWithoutLocation(
      User creator, String typeSlug, String title, OffsetDateTime startsAt) {
    Activity activity = newActivity(creator, typeSlug);
    activity.setTitle(title);
    activity.setStartsAt(startsAt);
    activity.setLocationLat(null);
    activity.setLocationLng(null);
    return activity;
  }
}
