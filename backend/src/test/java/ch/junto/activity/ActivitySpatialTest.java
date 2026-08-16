package ch.junto.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.activity.entity.Activity;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;

public class ActivitySpatialTest extends BaseIntegrationTest {

  @Autowired private ActivityRepository activityRepository;

  @Autowired private DatabaseHelper dbHelper;

  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

  @Test
  void shouldSaveAndLoadSpatialPoint() {
    User user = dbHelper.save(UserFixture.newUser("spatial.test@example.com"));
    Activity activity = ActivityFixture.newActivity(user, "climbing");

    Point zurich = geometryFactory.createPoint(new Coordinate(8.5417, 47.3769));
    activity.setLocation(zurich);
    activity.setLocationLng(8.5417);
    activity.setLocationLat(47.3769);

    Activity saved = activityRepository.save(activity);

    Optional<Activity> found = activityRepository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getLocation()).isNotNull();
    assertThat(found.get().getLocation().getX()).isEqualTo(8.5417);
    assertThat(found.get().getLocation().getY()).isEqualTo(47.3769);
    assertThat(found.get().getLocation().getSRID()).isEqualTo(4326);
  }
}
