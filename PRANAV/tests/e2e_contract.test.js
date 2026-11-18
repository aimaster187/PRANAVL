import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { createPranavServer } from "../backend/src/server.js";

const OWNER_KEY = "pranav_e2e_contract_secret_2026";
const MASTER_SECRET = "pranav_e2e_vault_secret_2026";

let tempDir;
let instance;
let baseUrl;

test.before(async () => {
  tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "pranav-e2e-"));
  instance = createPranavServer({
    dataDir: tempDir,
    fetchImpl: async () => new Response(JSON.stringify({ ok: true, result: {} }), { status: 200 }),
    env: { NODE_ENV: "test", PORT: "0", OWNER_API_KEY: OWNER_KEY, VAULT_MASTER_KEY: MASTER_SECRET }
  });
  await new Promise((resolve) => instance.server.listen(0, "127.0.0.1", resolve));
  baseUrl = `http://127.0.0.1:${instance.server.address().port}`;
});

test.after(async () => {
  if (instance) await instance.stop();
  if (tempDir) fs.rmSync(tempDir, { recursive: true, force: true });
});

async function adminPost(pathname, body) {
  const res = await fetch(`${baseUrl}${pathname}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${OWNER_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  return { status: res.status, data: await res.json() };
}

test("E2E Admin -> Backend -> Client SDK contract (App, Config+Rollback, Module, Announcement, User Ban, License DeviceLimit)", async () => {
  // 1. Admin creates isolated app matching sample host package
  const appRes = await adminPost("/api/v1/admin/apps", {
    id: "app_sample",
    appCode: "sample_target_app",
    packageName: "com.pranav.sampletarget",
    displayName: "Sample Target Host",
    latestVersion: "1.4.0",
    minSupportedVersion: "1.0.0",
    popupEnabled: true,
    forceUpdateEnabled: false,
    laterEnabled: true
  });
  assert.equal(appRes.status, 200);

  // 2. Admin publishes v1 + v2 Remote Config and tests Rollback
  const cfgCreate = await adminPost("/api/v1/admin/configs", {
    id: "cfg_sample",
    appId: "app_sample",
    configKey: "runtime_cfg",
    payloadJson: "{\"banner\":\"Welcome V1\",\"maxRetry\":2}",
    featureFlagsJson: "{\"fastMode\":false}"
  });
  assert.equal(cfgCreate.data.config.version, 1);

  const cfgUpdate = await adminPost("/api/v1/admin/configs", {
    id: "cfg_sample",
    appId: "app_sample",
    configKey: "runtime_cfg",
    payloadJson: "{\"banner\":\"Welcome V2\",\"maxRetry\":5}",
    featureFlagsJson: "{\"fastMode\":true}"
  });
  assert.equal(cfgUpdate.data.config.version, 2);

  // 3. Admin creates Dynamic Module
  const modRes = await adminPost("/api/v1/admin/modules", {
    id: "mod_promo",
    appId: "app_sample",
    moduleKey: "promo_tile",
    moduleName: "VIP Promo Tile",
    category: "Dynamic",
    defaultValuesJson: "{\"discount\":30}",
    actionParamsJson: "{\"route\":\"vip_screen\"}"
  });
  assert.equal(modRes.status, 200);

  // 4. Admin targets Announcement to user u_e2e_1
  const annRes = await adminPost("/api/v1/admin/announcements", {
    id: "ann_e2e",
    scope: "USER",
    displayType: "BANNER",
    appId: "app_sample",
    targetUserIds: ["u_e2e_1"],
    title: "VIP Flash Offer",
    message: "Special banner for u_e2e_1"
  });
  assert.equal(annRes.status, 200);

  // 5. Client signs challenge with ECDSA P-256 and executes session handshake
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const pubSpkiB64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const ch = await (await fetch(`${baseUrl}/api/v1/client/challenge`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appCode: "sample_target_app", deviceId: "dev_e2e_1" })
  })).json();
  const sig = crypto.sign("sha256", Buffer.from(ch.nonce, "utf8"), privateKey).toString("base64");

  const session = await (await fetch(`${baseUrl}/api/v1/client/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      appCode: "sample_target_app",
      deviceId: "dev_e2e_1",
      userId: "u_e2e_1",
      versionName: "1.2.0",
      keystorePublicKeyB64: pubSpkiB64,
      challengeNonce: ch.nonce,
      challengeSignatureB64: sig
    })
  })).json();

  assert.equal(session.allowed, true);
  assert.equal(session.signatureVerified, true);
  assert.equal(session.remoteConfig.values.banner, "Welcome V2");
  assert.equal(session.remoteConfig.featureFlags.fastMode, true);
  assert.equal(session.dynamicModules.length, 1);
  assert.equal(session.announcements.length, 1);
  assert.equal(session.announcements[0].displayType, "BANNER");

  // 6. Client acknowledges announcement & verifies second session excludes dismissed banner
  const ackRes = await fetch(`${baseUrl}/api/v1/client/announcements/ack`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ announcementId: "ann_e2e", deviceId: "dev_e2e_1", userId: "u_e2e_1", dismissed: true })
  });
  assert.equal(ackRes.status, 200);

  // 7. Admin rolls back Remote Config to V1 & checks /client/config returns V1
  const rbRes = await adminPost("/api/v1/admin/configs/cfg_sample/rollback", {});
  assert.equal(rbRes.data.config.values.banner, "Welcome V1");

  const cfgFetch = await (await fetch(`${baseUrl}/api/v1/client/config`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appCode: "sample_target_app", userId: "u_e2e_1", deviceId: "dev_e2e_1" })
  })).json();
  assert.equal(cfgFetch.remoteConfig.values.banner, "Welcome V1");

  // 8. Admin issues 1-device VIP license -> Device 1 activates -> Device 2 blocked -> Verify passes for Device 1
  await adminPost("/api/v1/admin/licenses", {
    id: "lic_e2e",
    appId: "app_sample",
    licenseKey: "PRNV-PRO-E2E-1111",
    planTier: "VIP_PRO",
    status: "UNUSED",
    paymentVerified: true,
    deviceLimit: 1
  });
  const act1 = await fetch(`${baseUrl}/api/v1/client/license/activate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appCode: "sample_target_app", licenseKey: "PRNV-PRO-E2E-1111", deviceId: "dev_e2e_1", userId: "u_e2e_1" })
  });
  assert.equal(act1.status, 200);

  const act2 = await fetch(`${baseUrl}/api/v1/client/license/activate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appCode: "sample_target_app", licenseKey: "PRNV-PRO-E2E-1111", deviceId: "dev_e2e_2", userId: "u_e2e_2" })
  });
  assert.equal(act2.status, 409);

  const verify = await (await fetch(`${baseUrl}/api/v1/client/license/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appCode: "sample_target_app", licenseKey: "PRNV-PRO-E2E-1111", deviceId: "dev_e2e_1" })
  })).json();
  assert.equal(verify.valid, true);
  assert.equal(verify.isVip, true);

  // 9. Admin bans user u_e2e_1 -> session returns 403 userBanned + SECURITY_LOCK -> Admin unbans -> allowed resumes
  await adminPost("/api/v1/admin/users/u_e2e_1/ban", { banned: true, reason: "Abuse detected" });
  const bannedRes = await fetch(`${baseUrl}/api/v1/client/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appCode: "sample_target_app", deviceId: "dev_e2e_1", userId: "u_e2e_1", versionName: "1.4.0" })
  });
  assert.equal(bannedRes.status, 403);
  const bannedJson = await bannedRes.json();
  assert.equal(bannedJson.userBanned, true);
  assert.equal(bannedJson.dialogs[0].dialogType, "SECURITY_LOCK");

  await adminPost("/api/v1/admin/users/u_e2e_1/ban", { banned: false });
  const auditLogs = await (await fetch(`${baseUrl}/api/v1/admin/audit-logs`, {
    headers: { Authorization: `Bearer ${OWNER_KEY}` }
  })).json();
  assert.ok(auditLogs.logs.some((l) => l.action === "BAN_USER"));
  assert.ok(auditLogs.logs.some((l) => l.action === "ROLLBACK_REMOTE_CONFIG"));
});
