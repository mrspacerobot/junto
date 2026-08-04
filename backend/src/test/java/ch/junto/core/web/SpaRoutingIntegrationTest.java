package ch.junto.core.web;

import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaRoutingIntegrationTest extends BaseIntegrationTest {

    private static final Pattern ASSET_URL = Pattern.compile("(?:src|href)=\"(/assets/[^\"]+)\"");

    @Autowired
    private UserRepository userRepository;

    @ParameterizedTest
    @ValueSource(strings = {
        "/",
        "/auth/login",
        "/auth/verify?token=example",
        "/activities",
        "/activities/2af4dce8-b431-4f6b-94b0-0c193b0a6d40",
        "/messages/2af4dce8-b431-4f6b-94b0-0c193b0a6d40",
        "/profile"
    })
    void anonymousVueRoutesReturnUncachedSpaShell(String route) throws Exception {
        mockMvc.perform(get(route))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("<div id=\"app\"></div>")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void directIndexRequestIsPublicAndUncached() throws Exception {
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("<div id=\"app\"></div>")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void authenticatedDeepLinkAlsoReturnsSpaShell() throws Exception {
        mockMvc.perform(get("/saved").session(authenticatedSession("spa-authenticated")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<div id=\"app\"></div>")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void builtHashedAssetIsPublicAndImmutable() throws Exception {
        MvcResult shell = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn();

        Matcher matcher = ASSET_URL.matcher(shell.getResponse().getContentAsString());
        assertThat(matcher.find()).as("index.html references a built hashed asset").isTrue();
        String assetUrl = matcher.group(1);
        assertThat(assetUrl).matches("/assets/.+-[A-Za-z0-9_-]+\\.[A-Za-z0-9]+$");

        mockMvc.perform(get(assetUrl))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, allOf(
                containsString("public"),
                containsString("max-age=31536000"),
                containsString("immutable")
            )));
    }

    @Test
    void protectedApiRejectsAnonymousAndAllowsAuthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/activity-types"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(not(containsString("<div id=\"app\"></div>"))));

        mockMvc.perform(get("/api/activity-types").session(authenticatedSession("api-authenticated")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void unknownApiNeverFallsThroughToSpaHtml() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(not(containsString("<div id=\"app\"></div>"))));

        mockMvc.perform(get("/api/does-not-exist").session(authenticatedSession("unknown-api")))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
            .andExpect(content().string(not(containsString("<div id=\"app\"></div>"))));
    }

    @Test
    void authenticationResponsesCannotBeCached() throws Exception {
        mockMvc.perform(post("/api/auth/request-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cache-test@junto.app\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, allOf(
                containsString("no-store"),
                containsString("max-age=0")
            )));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void websocketEndpointIsNotHandledBySpaFallback() throws Exception {
        mockMvc.perform(get("/ws"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(not(containsString("<div id=\"app\"></div>"))));

        mockMvc.perform(get("/ws/not-an-endpoint"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(not(containsString("<div id=\"app\"></div>"))));
    }

    private MockHttpSession authenticatedSession(String emailPrefix) {
        User user = userRepository.save(UserFixture.persistableWithUniqueEmail(emailPrefix));
        return MockMvcTestHelper.sessionFor(user);
    }
}
