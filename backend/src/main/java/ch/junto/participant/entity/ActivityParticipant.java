package ch.junto.participant.entity;

import ch.junto.activity.entity.Activity;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "activity_participants",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"activity_id", "user_id"})})
public class ActivityParticipant {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "activity_id", nullable = false)
  private Activity activity;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ParticipantRole role = ParticipantRole.PARTICIPANT;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ParticipantStatus status = ParticipantStatus.PENDING;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = OffsetDateTime.now();
    updatedAt = OffsetDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Activity getActivity() {
    return activity;
  }

  public void setActivity(Activity activity) {
    this.activity = activity;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public ParticipantRole getRole() {
    return role;
  }

  public void setRole(ParticipantRole role) {
    this.role = role;
  }

  public ParticipantStatus getStatus() {
    return status;
  }

  public void setStatus(ParticipantStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
