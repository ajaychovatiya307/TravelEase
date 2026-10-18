#!/usr/bin/env node
/**
 * LOCAL DEVELOPMENT / TESTING ONLY.
 *
 * Generates an HS256 JWT compatible with the services' shared-secret verification
 * (api-contract.md §2: Bearer token, `roles` claim) for exercising APIs locally.
 *
 * There is no built-in secret. Supply the same development secret your local services use,
 * via --secret or the TRAVELEASE_JWT_SECRET environment variable. The script refuses to run
 * when TRAVELEASE_ENV/NODE_ENV is "production", refuses short or previously published secrets,
 * and only issues EMPLOYEE tokens unless --allow-privileged is given.
 *
 * Usage:
 *   TRAVELEASE_JWT_SECRET=<dev secret> node generate-token.js [--sub <userId>] [--roles ROLE1,ROLE2]
 *       [--expiresIn <seconds>] [--allow-privileged]
 *
 * Example:
 *   TRAVELEASE_JWT_SECRET=... node generate-token.js --sub 2 --roles EMPLOYEE,MANAGER --allow-privileged
 */

const crypto = require("crypto");

const DEFAULT_SUB = "1";
const DEFAULT_ROLES = ["EMPLOYEE"];
const DEFAULT_EXPIRES_IN_SECONDS = 3600;
const MAX_EXPIRES_IN_SECONDS = 8 * 3600;
const MIN_SECRET_BYTES = 32;
const PRIVILEGED_ROLES = new Set(["MANAGER", "FINANCE", "ADMIN"]);
const KNOWN_ROLES = new Set(["EMPLOYEE", ...PRIVILEGED_ROLES]);

/** SHA-256 digests of secrets that were published in source control (mirrors common-lib JwtSecretPolicy). */
const COMPROMISED_SECRET_SHA256 = new Set([
    "46d120b95b10c1d80d1fba8636d5001d13230578f3ca3de4ee23897f1dc97c89",
]);

function parseArgs(argv, env) {
    const args = {
        sub: DEFAULT_SUB,
        roles: DEFAULT_ROLES,
        secret: env.TRAVELEASE_JWT_SECRET,
        expiresIn: DEFAULT_EXPIRES_IN_SECONDS,
        allowPrivileged: false,
    };
    for (let i = 0; i < argv.length; i += 1) {
        const flag = argv[i];
        const value = argv[i + 1];
        switch (flag) {
            case "--sub":
                args.sub = value;
                i += 1;
                break;
            case "--roles":
                args.roles = String(value).split(",").map((role) => role.trim().toUpperCase()).filter(Boolean);
                i += 1;
                break;
            case "--secret":
                args.secret = value;
                i += 1;
                break;
            case "--expiresIn":
                args.expiresIn = Number(value);
                i += 1;
                break;
            case "--allow-privileged":
                args.allowPrivileged = true;
                break;
            default:
                throw new Error(`Unknown argument: ${flag}`);
        }
    }
    return args;
}

/** Throws with a message that never includes the secret. */
function validate(args, env) {
    const mode = String(env.TRAVELEASE_ENV || env.NODE_ENV || "").toLowerCase();
    if (mode === "production" || mode === "prod") {
        throw new Error("Refusing to run: generate-token.js is for local development/testing only.");
    }
    if (!args.secret) {
        throw new Error("No secret provided. Pass --secret or set TRAVELEASE_JWT_SECRET to your local development secret.");
    }
    if (Buffer.byteLength(args.secret, "utf8") < MIN_SECRET_BYTES) {
        throw new Error(`Secret is too short: at least ${MIN_SECRET_BYTES} bytes are required for HS256.`);
    }
    const digest = crypto.createHash("sha256").update(args.secret, "utf8").digest("hex");
    if (COMPROMISED_SECRET_SHA256.has(digest)) {
        throw new Error("Refusing to sign with a secret that was published in source control.");
    }
    if (!args.sub || !/^\d+$/.test(String(args.sub))) {
        throw new Error("--sub must be a numeric user id.");
    }
    if (args.roles.length === 0 || args.roles.some((role) => !KNOWN_ROLES.has(role))) {
        throw new Error(`--roles must be a comma-separated list of: ${[...KNOWN_ROLES].join(", ")}.`);
    }
    const privileged = args.roles.filter((role) => PRIVILEGED_ROLES.has(role));
    if (privileged.length > 0 && !args.allowPrivileged) {
        throw new Error(`Roles ${privileged.join(", ")} are privileged; add --allow-privileged to issue them deliberately.`);
    }
    if (!Number.isInteger(args.expiresIn) || args.expiresIn <= 0 || args.expiresIn > MAX_EXPIRES_IN_SECONDS) {
        throw new Error(`--expiresIn must be a whole number of seconds between 1 and ${MAX_EXPIRES_IN_SECONDS}.`);
    }
}

function base64url(input) {
    return Buffer.from(input).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function signToken({ sub, roles, secret, expiresIn }, nowSeconds = Math.floor(Date.now() / 1000)) {
    const header = { alg: "HS256", typ: "JWT" };
    const payload = { sub: String(sub), roles, iat: nowSeconds, exp: nowSeconds + expiresIn };

    const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(payload))}`;
    const signature = crypto.createHmac("sha256", secret).update(signingInput).digest("base64")
        .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

    return `${signingInput}.${signature}`;
}

function generate(argv, env) {
    const args = parseArgs(argv, env);
    validate(args, env);
    return signToken(args);
}

function main() {
    try {
        const token = generate(process.argv.slice(2), process.env);
        console.log(token);
        console.log(`\nAuthorization: Bearer ${token}`);
    } catch (error) {
        console.error(`generate-token: ${error.message}`);
        process.exitCode = 1;
    }
}

if (require.main === module) {
    main();
}

module.exports = { generate, parseArgs, validate, signToken };
