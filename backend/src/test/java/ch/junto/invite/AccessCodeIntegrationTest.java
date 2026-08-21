package ch.junto.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.junto.auth.AuthTokenRepository;
import ch.junto.auth.entity.AuthToken;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.email.EmailService;
import ch.junto.fixtures.InviteCodeFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.invite.entity.AccessCode;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Shared access code signup")
class AccessCodeIntegrationTest extends BaseIntegrationTest {

  private static final String SHARED_CODE = "JUNTO-FOUNDERS";

  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthTokenRepository authTokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private InviteCodeRepository inviteCodeRepository;
  @Autowired private AccessCodeRepository accessCodeRepository;
  @Autowired private AccessCodeRedemptionRepository accessCodeRedemptionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private EmailService emailService;

  private AccessCode sharedCode;

  @BeforeEach
  void seedSharedCode() {
    User administrator = userRepository.save(UserFixture.newAdmin(uniqueEmail("admin")));
    AccessCode accessCode = new AccessCode();
    accessCode.setCode(SHARED_CODE);
    accessCode.setLabel(AccessCodeService.BOOTSTRAP_LABEL);
    accessCode.setCreatedBy(administrator);
    sharedCode = accessCodeRepository.saveAndFlush(accessCode);
  }

  @Test
  @DisplayName("admits more than one account and stays usable")
  void admitsMoreThanOneAccount() throws Exception {
    User first = signUp(uniqueEmail("first"), SHARED_CODE);
    User second = signUp(uniqueEmail("second"), SHARED_CODE);

    assertThat(first.getId()).isNotEqualTo(second.getId());
    assertThat(accessCodeRedemptionRepository.countByAccessCodeId(sharedCode.getId())).isEqualTo(2);
    assertThat(redeemedBy(first)).isTrue();
    assertThat(redeemedBy(second)).isTrue();
    assertThat(accessCodeRepository.findByCode(SHARED_CODE).orElseThrow().isRevoked()).isFalse();
  }

  @Test
  @DisplayName("ignores the case the member typed")
  void ignoresTypedCase() throws Exception {
    User member = signUp(uniqueEmail("mixed"), " junto-Founders ");

    assertThat(redeemedBy(member)).isTrue();
  }

  @Test
  @DisplayName("still admits accounts long after it was created, because it never expires")
  void neverExpires() throws Exception {
    jdbcTemplate.update(
        "UPDATE access_codes SET created_at = ? WHERE id = ?",
        OffsetDateTime.now().minusYears(5),
        sharedCode.getId());

    User member = signUp(uniqueEmail("years-later"), SHARED_CODE);

    assertThat(redeemedBy(member)).isTrue();
  }

  @Test
  @DisplayName("stops admitting accounts once revoked, keeping earlier admissions on record")
  void revocationStopsFurtherAdmissions() throws Exception {
    User admitted = signUp(uniqueEmail("before-revocation"), SHARED_CODE);

    assertThat(revokeSharedCode()).isTrue();

    requestSignupCode(uniqueEmail("after-revocation"), SHARED_CODE)
        .andExpect(status().isBadRequest());
    assertThat(accessCodeRedemptionRepository.countByAccessCodeId(sharedCode.getId())).isEqualTo(1);
    assertThat(redeemedBy(admitted)).isTrue();
    assertThat(accessCodeRepository.findByCode(SHARED_CODE)).isPresent();
  }

  @Test
  @DisplayName("revoking twice reports no second change")
  void revokingTwiceChangesNothing() {
    assertThat(revokeSharedCode()).isTrue();
    assertThat(revokeSharedCode()).isFalse();
  }

  @Test
  @DisplayName("answers identically for unknown, revoked and used codes")
  void answersIdenticallyForEveryUnavailableCode() throws Exception {
    InviteCode invite = seedInvite();
    signUp(uniqueEmail("invite-user"), invite.getCode());
    revokeSharedCode();

    String unknown = rejectedSignupResponse(uniqueEmail("a"), "JUNTO-NOTHING");
    String revoked = rejectedSignupResponse(uniqueEmail("b"), SHARED_CODE);
    String used = rejectedSignupResponse(uniqueEmail("c"), invite.getCode());

    assertThat(revoked).isEqualTo(unknown);
    assertThat(used).isEqualTo(unknown);
  }

  @Test
  @DisplayName("leaves a personal invite single-use")
  void leavesPersonalInvitesSingleUse() throws Exception {
    InviteCode invite = seedInvite();

    User admitted = signUp(uniqueEmail("only"), invite.getCode());

    requestSignupCode(uniqueEmail("second"), invite.getCode()).andExpect(status().isBadRequest());
    InviteCode consumed = inviteCodeRepository.findByCode(invite.getCode()).orElseThrow();
    assertThat(consumed.getUsedBy().getId()).isEqualTo(admitted.getId());
  }

  private InviteCode seedInvite() {
    User inviter = userRepository.save(UserFixture.newAdmin(uniqueEmail("inviter")));
    InviteCode invite = InviteCodeFixture.valid(inviter);
    invite.setId(null);
    return inviteCodeRepository.saveAndFlush(invite);
  }

  private boolean redeemedBy(User user) {
    return accessCodeRedemptionRepository.existsByAccessCodeIdAndUserId(
        sharedCode.getId(), user.getId());
  }

  private boolean revokeSharedCode() {
    return jdbcTemplate.update(
            "UPDATE access_codes SET revoked_at = now() WHERE label = ? AND revoked_at IS NULL",
            AccessCodeService.BOOTSTRAP_LABEL)
        == 1;
  }

  private User signUp(String email, String submittedCode) throws Exception {
    requestSignupCode(email, submittedCode).andExpect(status().isOk());
    AuthToken token =
        authTokenRepository
            .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
            .orElseThrow();
    mockMvc
        .perform(postJson("/api/auth/verify", new VerifyBody(email, token.getCode())))
        .andExpect(status().isOk());
    return userRepository.findByEmail(email).orElseThrow();
  }

  private ResultActions requestSignupCode(String email, String submittedCode) throws Exception {
    return mockMvc.perform(
        postJson("/api/auth/signup/request-code", new SignupBody(email, submittedCode)));
  }

  private String rejectedSignupResponse(String email, String submittedCode) throws Exception {
    MvcResult result =
        requestSignupCode(email, submittedCode).andExpect(status().isBadRequest()).andReturn();
    return result.getResponse().getContentAsString();
  }

  /** Every request uses a fresh source: rate-limit rows commit outside the test transaction. */
  private MockHttpServletRequestBuilder postJson(String path, Object body) {
    String source = UUID.randomUUID().toString();
    return post(path)
        .with(
            request -> {
              request.setRemoteAddr(source);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(MockMvcTestHelper.json(objectMapper, body));
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@junto.app";
  }

  private record SignupBody(String email, String inviteCode) {}

  private record VerifyBody(String email, String code) {}
}
