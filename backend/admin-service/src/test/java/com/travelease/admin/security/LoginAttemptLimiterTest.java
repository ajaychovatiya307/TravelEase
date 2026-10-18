package com.travelease.admin.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.travelease.common.exception.TooManyRequestsException;

class LoginAttemptLimiterTest {

    /** Mutable clock so lock expiry can be tested without sleeping. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-18T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(5, 50, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);

    @Test
    @DisplayName("locks a username after 5 failures, for existing and unknown usernames alike")
    void locksAfterRepeatedFailures() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("Asha.Rao@travelease.example", "10.0.0.1");
            limiter.checkAllowed("asha.rao@travelease.example", "10.0.0.1");
        }
        limiter.recordFailure("asha.rao@travelease.example", "10.0.0.2");

        // normalized username: case and client address do not help an attacker
        assertThatThrownBy(() -> limiter.checkAllowed("ASHA.RAO@TRAVELEASE.EXAMPLE", "10.0.0.9"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkAllowed("someone.else@travelease.example", "10.0.0.9")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the lock expires after the lock duration")
    void lockExpires() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("ghost@travelease.example", "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("ghost@travelease.example", "10.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.checkAllowed("ghost@travelease.example", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a successful sign-in clears the username's failure count")
    void successResets() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("rahul@travelease.example", "10.0.0.1");
        }
        limiter.recordSuccess("rahul@travelease.example");
        limiter.recordFailure("rahul@travelease.example", "10.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("rahul@travelease.example", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one client trying many usernames is throttled per client")
    void sprayingFromOneClientIsLimited() {
        LoginAttemptLimiter strict = new LoginAttemptLimiter(5, 3, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
        strict.recordFailure("a@x.com", "203.0.113.7");
        strict.recordFailure("b@x.com", "203.0.113.7");
        strict.recordFailure("c@x.com", "203.0.113.7");

        assertThatThrownBy(() -> strict.checkAllowed("d@x.com", "203.0.113.7"))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
