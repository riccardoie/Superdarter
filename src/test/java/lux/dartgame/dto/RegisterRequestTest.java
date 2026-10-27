package lux.dartgame.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short1", "abcdef1", "abcdefgh", "long-password", "        "})
    void rejectsInvalidPasswords(final String password) {
        var request = new RegisterRequest("alice", password, "alice@example.com");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefg1", "1abcdefg", "long-password1", "12345678"})
    void acceptsValidPasswords(final String password) {
        assertThat(validator.validate(
                new RegisterRequest("alice", password, "alice@example.com"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"alice bob", " alice", "alice ", "alice\tbob", "alice\nbob"})
    void rejectsInvalidUsernames(final String username) {
        var request = new RegisterRequest(username, "password1", "alice@example.com");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("username"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "alice-bob", "alice_bob", "alice123"})
    void acceptsUsernamesWithoutWhitespace(final String username) {
        assertThat(validator.validate(
                new RegisterRequest(username, "password1", "alice@example.com"))).isEmpty();
    }
}
