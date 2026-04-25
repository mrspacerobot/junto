package ch.junto.participant;

import ch.junto.activity.ActivityRepository;
import ch.junto.participant.dto.ParticipantResponse;
import ch.junto.activity.entity.Activity;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.user.entity.User;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.core.exception.ConflictException;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final ActivityRepository activityRepository;

    public ParticipantService(ParticipantRepository participantRepository, ActivityRepository activityRepository) {
        this.participantRepository = participantRepository;
        this.activityRepository = activityRepository;
    }

    public void addCreator(Activity activity, User creator) {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setActivity(activity);
        participant.setUser(creator);
        participant.setRole(ParticipantRole.CREATOR);
        participant.setStatus(ParticipantStatus.APPROVED);
        participantRepository.save(participant);
    }

    public ParticipantResponse join(UUID activityId, User user) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (activity.getStatus() != ActivityStatus.ACTIVE) {
            throw new ConflictException("Activity is not active");
        }

        OffsetDateTime deadline = activity.getSignupDeadline() != null ? activity.getSignupDeadline() : activity.getStartsAt();
        if (OffsetDateTime.now().isAfter(deadline)) {
            throw new ConflictException("Signup deadline has passed");
        }

        if (participantRepository.findByActivityIdAndUserId(activityId, user.getId()).isPresent()) {
            throw new ConflictException("Already a participant");
        }

        long count = participantRepository.countByActivityIdAndStatus(activityId, ParticipantStatus.APPROVED);
        if (count >= activity.getMaxParticipants()) {
            throw new ConflictException("Activity is full");
        }

        ActivityParticipant participant = new ActivityParticipant();
        participant.setActivity(activity);
        participant.setUser(user);
        participant.setRole(ParticipantRole.PARTICIPANT);
        
        if (activity.getJoinPolicy() == JoinPolicy.OPEN) {
            participant.setStatus(ParticipantStatus.APPROVED);
        } else {
            participant.setStatus(ParticipantStatus.PENDING);
        }

        participant = participantRepository.save(participant);
        return mapToResponse(participant);
    }

    public void leave(UUID activityId, User user) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        OffsetDateTime deadline = activity.getSignupDeadline() != null ? activity.getSignupDeadline() : activity.getStartsAt();
        if (OffsetDateTime.now().isAfter(deadline)) {
            throw new ConflictException("Signup deadline has passed");
        }

        ActivityParticipant participant = participantRepository.findByActivityIdAndUserId(activityId, user.getId())
            .orElseThrow(() -> new NotFoundException("Not a participant"));

        if (participant.getRole() == ParticipantRole.CREATOR) {
            throw new ConflictException("Creator cannot leave activity");
        }

        participantRepository.delete(participant);
    }

    public void approve(UUID participantId, User creator) {
        ActivityParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new NotFoundException("Participant not found"));

        if (!participant.getActivity().getCreator().getId().equals(creator.getId())) {
            throw new ForbiddenException("Only creator can approve participants");
        }

        if (participant.getStatus() != ParticipantStatus.PENDING) {
            throw new ConflictException("Participant is not pending");
        }

        long count = participantRepository.countByActivityIdAndStatus(participant.getActivity().getId(), ParticipantStatus.APPROVED);
        if (count >= participant.getActivity().getMaxParticipants()) {
            throw new ConflictException("Activity is full");
        }

        participant.setStatus(ParticipantStatus.APPROVED);
        participantRepository.save(participant);
    }

    public void decline(UUID participantId, User creator) {
        ActivityParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new NotFoundException("Participant not found"));

        if (!participant.getActivity().getCreator().getId().equals(creator.getId())) {
            throw new ForbiddenException("Only creator can decline participants");
        }

        if (participant.getStatus() != ParticipantStatus.PENDING) {
            throw new ConflictException("Participant is not pending");
        }

        participant.setStatus(ParticipantStatus.DECLINED);
        participantRepository.save(participant);
    }

    @Transactional
    public int autoDeclineExpiredRequests() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ActivityParticipant> expired = participantRepository.findAll((root, query, cb) -> 
            cb.and(
                cb.equal(root.get("status"), ParticipantStatus.PENDING),
                cb.or(
                    cb.and(
                        cb.isNotNull(root.get("activity").get("signupDeadline")),
                        cb.lessThan(root.get("activity").get("signupDeadline"), now)
                    ),
                    cb.and(
                        cb.isNull(root.get("activity").get("signupDeadline")),
                        cb.lessThan(root.get("activity").get("startsAt"), now)
                    )
                )
            )
        );

        for (ActivityParticipant p : expired) {
            p.setStatus(ParticipantStatus.DECLINED);
        }
        participantRepository.saveAll(expired);
        return expired.size();
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> listParticipants(UUID activityId) {
        return participantRepository.findAllByActivityId(activityId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getTopParticipantAvatars(UUID activityId, int limit) {
        return participantRepository.findAllByActivityIdAndStatus(activityId, ParticipantStatus.APPROVED).stream()
            .limit(limit)
            .map(p -> p.getUser().getAvatarUrl())
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countApproved(UUID activityId) {
        return participantRepository.countByActivityIdAndStatus(activityId, ParticipantStatus.APPROVED);
    }

    private ParticipantResponse mapToResponse(ActivityParticipant p) {
        return new ParticipantResponse(
            p.getId(),
            p.getUser().getId(),
            p.getUser().getDisplayName(),
            p.getUser().getAvatarUrl(),
            p.getRole(),
            p.getStatus()
        );
    }
}
