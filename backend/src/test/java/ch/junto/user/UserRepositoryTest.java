package ch.junto.user;

import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class UserRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseHelper dbHelper;

    @Test
    void shouldFindUserByEmail() {
        dbHelper.save(UserFixture.newUser("helper.test@example.com"));

        assertThat(userRepository.findByEmail("helper.test@example.com")).isPresent();
    }
}
