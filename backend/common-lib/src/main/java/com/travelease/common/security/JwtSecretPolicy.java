package com.travelease.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Validates the shared HS256 signing secret ({@code security.jwt.secret}) before any service
 * builds a {@code JwtDecoder}/{@code JwtEncoder} from it.
 *
 * <p>Rejects a missing/blank secret, a secret shorter than the 256 bits HS256 requires, and any
 * secret that was previously committed to the repository. Known-compromised values are matched by
 * SHA-256 digest so the secret itself never appears in source again.</p>
 */
public final class JwtSecretPolicy {

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    /** SHA-256 digests of signing secrets that were published in source control and must never be used. */
    private static final Set<String> COMPROMISED_SECRET_SHA256 = Set.of(
            "46d120b95b10c1d80d1fba8636d5001d13230578f3ca3de4ee23897f1dc97c89");

    private JwtSecretPolicy() {
        // utility holder - not instantiable
    }

    /**
     * Returns the secret unchanged when it is safe to use; otherwise throws.
     *
     * @param secret the configured signing secret
     * @return the same secret
     * @throws IllegalStateException when the secret is missing, too short, or known to be compromised.
     *         The message never contains the secret value.
     */
    public static String requireUsableSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret is not configured. Set the TRAVELEASE_JWT_SECRET environment variable "
                            + "(at least " + MIN_SECRET_BYTES + " random bytes, e.g. `openssl rand -base64 48`).");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret is too short for HS256: at least " + MIN_SECRET_BYTES + " bytes are required.");
        }
        if (isCompromised(secret)) {
            throw new IllegalStateException(
                    "security.jwt.secret matches a value that was published in source control. Generate a new secret.");
        }
        return secret;
    }

    static boolean isCompromised(String secret) {
        return COMPROMISED_SECRET_SHA256.contains(sha256Hex(secret));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
