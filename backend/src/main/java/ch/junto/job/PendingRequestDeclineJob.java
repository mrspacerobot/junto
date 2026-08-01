package ch.junto.job;

import ch.junto.participant.ParticipantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingRequestDeclineJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRequestDeclineJob.class);
    private final ParticipantService participantService;

    public PendingRequestDeclineJob(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @Scheduled(fixedDelay = 900000) // 15 min
    public void declineRequests() {
        int declined = participantService.autoDeclineExpiredRequests();
        if (declined > 0) {
            log.info("Auto-declined {} expired pending requests", declined);
        }
    }
}
