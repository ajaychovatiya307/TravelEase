package com.travelease.admin.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.travelease.common.exception.TooManyRequestsException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for credential checks (login, completing a forced password change) (#14).
 *
 * <p>Failures are counted per normalized username and per client address within a sliding window;
 * crossing a limit locks that key for the lock duration and every attempt during the lock gets the
 * same 429 response — for existing and non-existing usernames alike, so the limiter never reveals
 * whether an account exists. A successful check clears the username's counter.</p>
 *
 * <p>State is in memory: it protects a single admin-service instance and resets on restart. Running
 * several instances requires a shared store (documented in README).</p>
 */
@Component
public class LoginAttemptLimiter {

    static final String LOCKED_MESSAGE = "Too many failed attempts. Please wait and try again later.";

    private final int maxFailuresPerUsername;
    private final int maxFailuresPerClient;
    private final Duration window;
    private final Duration lockDuration;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public LoginAttemptLimiter(
            @Value("${travelease.login.max-failures-per-username:5}") int maxFailuresPerUsername,
            @Value("${travelease.login.max-failures-per-client:50}") int maxFailuresPerClient,
            @Value("${travelease.login.window:PT15M}") Duration window,
            @Value("${travelease.login.lock-duration:PT15M}") Duration lockDuration) {
        this(maxFailuresPerUsername, maxFailuresPerClient, window, lockDuration, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailuresPerUsername, int maxFailuresPerClient, Duration window, Duration lockDuration,
            Clock clock) {
        this.maxFailuresPerUsername = maxFailuresPerUsername;
        this.maxFailuresPerClient = maxFailuresPerClient;
        this.window = window;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    /** @throws TooManyRequestsException (HTTP 429) while the username or client is locked out */
    public void checkAllowed(String username, String client) {
        Instant now = clock.instant();
        if (isLocked(usernameKey(username), now) || isLocked(clientKey(client), now)) {
            throw new TooManyRequestsException(LOCKED_MESSAGE);
        }
    }

    public void recordFailure(String username, String client) {
        Instant now = clock.instant();
        register(usernameKey(username), maxFailuresPerUsername, now);
        register(clientKey(client), maxFailuresPerClient, now);
    }

    public void recordSuccess(String username) {
        attempts.remove(usernameKey(username));
    }

    private boolean isLocked(String key, Instant now) {
        Attempts entry = attempts.get(key);
        return entry != null && entry.lockedUntil != null && now.isBefore(entry.lockedUntil);
    }

    private void register(String key, int limit, Instant now) {
        attempts.compute(key, (k, existing) -> {
            Attempts entry = existing == null || now.isAfter(existing.windowStart.plus(window))
                    && (existing.lockedUntil == null || now.isAfter(existing.lockedUntil))
                    ? new Attempts(now) : existing;
            entry.failures++;
            if (entry.failures >= limit) {
                entry.lockedUntil = now.plus(lockDuration);
            }
            return entry;
        });
    }

    private static String usernameKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String clientKey(String client) {
        return "c:" + (client == null ? "unknown" : client);
    }

    private static final class Attempts {
        private final Instant windowStart;
        private int failures;
        private Instant lockedUntil;

        private Attempts(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
