package ch.junto.helpers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class TimeTestHelper {

  private TimeTestHelper() {}

  public static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  public static OffsetDateTime hoursFromNow(int hours) {
    return now().plusHours(hours);
  }

  public static OffsetDateTime hoursAgo(int hours) {
    return now().minusHours(hours);
  }
}
