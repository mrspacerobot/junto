package ch.junto.job;

import ch.junto.activity.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivityAutoCompleteJob {

  private static final Logger log = LoggerFactory.getLogger(ActivityAutoCompleteJob.class);
  private final ActivityService activityService;

  public ActivityAutoCompleteJob(ActivityService activityService) {
    this.activityService = activityService;
  }

  @Scheduled(cron = "0 0 * * * *") // Hourly
  public void autoComplete() {
    int completed = activityService.autoCompleteStartedActivities();
    if (completed > 0) {
      log.info("Auto-completed {} activities", completed);
    }
  }
}
