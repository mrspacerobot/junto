package ch.junto.invite.entity;

import ch.junto.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One account admitted by a shared {@link AccessCode}.
 *
 * <p>Rows are written by the atomic insert in {@code AccessCodeRedemptionRepository} and are never
 * deleted: together they are the record of how accounts entered the pilot, the role {@code
 * InviteCode#getUsedBy()} plays for a single-use invite.
 */
@Entity
@Table(
    name = "access_code_redemptions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_access_code_redemption",
            columnNames = {"access_code_id", "user_id"}))
public class AccessCodeRedemption {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "access_code_id", nullable = false)
  private AccessCode accessCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "redeemed_at", nullable = false)
  private OffsetDateTime redeemedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public AccessCode getAccessCode() {
    return accessCode;
  }

  public void setAccessCode(AccessCode accessCode) {
    this.accessCode = accessCode;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public OffsetDateTime getRedeemedAt() {
    return redeemedAt;
  }

  public void setRedeemedAt(OffsetDateTime redeemedAt) {
    this.redeemedAt = redeemedAt;
  }
}
