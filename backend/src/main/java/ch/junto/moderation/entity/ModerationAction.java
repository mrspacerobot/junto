package ch.junto.moderation.entity;

import ch.junto.moderation.enums.ModerationActionType;
import ch.junto.user.entity.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "moderation_actions")
public class ModerationAction {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "moderator_id", nullable = false)
  private User moderator;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_user_id", nullable = false)
  private User targetUser;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false)
  private ModerationActionType actionType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

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

  public User getModerator() {
    return moderator;
  }

  public void setModerator(User moderator) {
    this.moderator = moderator;
  }

  public User getTargetUser() {
    return targetUser;
  }

  public void setTargetUser(User targetUser) {
    this.targetUser = targetUser;
  }

  public ModerationActionType getActionType() {
    return actionType;
  }

  public void setActionType(ModerationActionType actionType) {
    this.actionType = actionType;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
