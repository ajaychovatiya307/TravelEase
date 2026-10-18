// Tests for the dev-only token script. Run with: node --test generate-token.test.js
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("crypto");
const { execFileSync } = require("child_process");
const path = require("path");
const { generate } = require("./generate-token");

const DEV_SECRET = "local-dev-secret-for-script-tests-0123456789";
// Previously hard-coded fallback; public in git history, present only to prove it is refused.
const PUBLISHED_SECRET = "dev-only-shared-secret-change-me-before-production-0000";

function decode(token) {
    const [header, payload, signature] = token.split(".");
    return { header, payload: JSON.parse(Buffer.from(payload, "base64url").toString("utf8")), signature };
}

test("refuses to run without an explicit secret (no built-in default)", () => {
    assert.throws(() => generate([], {}), /No secret provided/);
});

test("refuses the previously published secret", () => {
    assert.throws(() => generate(["--secret", PUBLISHED_SECRET], {}), /published in source control/);
});

test("refuses short secrets", () => {
    assert.throws(() => generate(["--secret", "short"], {}), /too short/);
});

test("refuses production mode", () => {
    assert.throws(() => generate([], { TRAVELEASE_JWT_SECRET: DEV_SECRET, TRAVELEASE_ENV: "production" }), /local development/);
    assert.throws(() => generate([], { TRAVELEASE_JWT_SECRET: DEV_SECRET, NODE_ENV: "production" }), /local development/);
});

test("does not issue privileged roles unless explicitly allowed", () => {
    assert.throws(() => generate(["--roles", "ADMIN"], { TRAVELEASE_JWT_SECRET: DEV_SECRET }), /privileged/);
    const token = generate(["--roles", "EMPLOYEE,MANAGER", "--allow-privileged", "--sub", "2"], { TRAVELEASE_JWT_SECRET: DEV_SECRET });
    assert.deepEqual(decode(token).payload.roles, ["EMPLOYEE", "MANAGER"]);
});

test("rejects unknown roles and excessive lifetimes", () => {
    assert.throws(() => generate(["--roles", "SUPERUSER"], { TRAVELEASE_JWT_SECRET: DEV_SECRET }), /--roles/);
    assert.throws(() => generate(["--expiresIn", "999999"], { TRAVELEASE_JWT_SECRET: DEV_SECRET }), /--expiresIn/);
});

test("default token is an EMPLOYEE token with a valid HS256 signature for the supplied secret", () => {
    const token = generate([], { TRAVELEASE_JWT_SECRET: DEV_SECRET });
    const { header, payload, signature } = decode(token);
    const expected = crypto.createHmac("sha256", DEV_SECRET).update(`${header}.${token.split(".")[1]}`).digest("base64url");
    assert.equal(signature, expected);
    assert.deepEqual(payload.roles, ["EMPLOYEE"]);
    assert.equal(payload.exp - payload.iat, 3600);
});

test("CLI exits non-zero without printing a token when no secret is configured", () => {
    let failed = false;
    try {
        execFileSync(process.execPath, [path.join(__dirname, "generate-token.js")], {
            env: { PATH: process.env.PATH },
            stdio: "pipe",
        });
    } catch (error) {
        failed = true;
        assert.equal(error.status, 1);
        assert.equal(error.stdout.toString(), "");
    }
    assert.ok(failed);
});
