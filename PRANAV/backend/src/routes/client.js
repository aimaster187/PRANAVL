import crypto from "node:crypto";
import { checkRateLimit, readJsonBody, sendJson } from "../httpHelpers.js";
import { sha256Hex, verifyDeviceChallengeSignature } from "../cryptoVault.js";

function compareSemver(a, b) {
  const pa = String(a || "0.0.0").split(".").map((x) => parseInt(x, 10) || 0);
  const pb = String(b || "0.0.0").split(".").map((x) => parseInt(x, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const va = pa[i] || 0;
    const vb = pb[i] || 0;
    if (va < vb) return -1;
    if (va > vb) return 1;
  }
  return 0;
}

function inRolloutBucket(deviceId, appCode, rolloutPercent) {
  const pct = Number(rolloutPercent ?? 100);
  if (pct >= 100) return true;
  if (pct <= 0) return false;
  const hash = sha256Hex(`${deviceId}:${appCode}`);
  const bucket = parseInt(hash.slice(0, 4), 16) % 100;
  return bucket < pct;
}

function isLicenseExpired(lic, now = Date.now()) {
  if (!lic.expiresAtEpochMs || Number(lic.expiresAtEpochMs) <= 0) return false;
  const graceMs = Number(lic.graceDays || 0) * 86_400_000;
  return now > Number(lic.expiresAtEpochMs) + graceMs;
}

export async function handleClientRoutes(req, res, pathname, { config, storage }) {
  if (!pathname.startsWith("/api/v1/client")) return false;
  checkRateLimit(req, 300, config.environment === "test");

  if (req.method === "POST" && pathname === "/api/v1/client/challenge") {
    const body = await readJsonBody(req);
    const nonce = crypto.randomBytes(24).toString("base64url");
    storage.state.nonces[nonce] = {
      appCode: String(body.appCode || ""),
      deviceId: String(body.deviceId || ""),
      createdAtMs: Date.now()
    };
    storage.save();
    return sendJson(res, 200, { ok: true, nonce, expiresInMs: 300000 });
  }

  if (req.method === "POST" && (pathname === "/api/v1/client/session" || pathname === "/api/v1/client/handshake")) {
    const body = await readJsonBody(req);
    const appCode = String(body.appCode || body.packageName || "").trim();
    const deviceId = String(body.deviceId || body.installUuid || "").trim();
    const userId = String(body.userId || body.uid || "").trim();
    const versionName = String(body.versionName || "1.0.0").trim();
    const versionCode = Number(body.versionCode || 1);
    const keystoreHash = String(body.keystoreHash || "").trim();
    const keystorePubKey = String(body.keystorePublicKeyB64 || "").trim();
    const challengeNonce = String(body.challengeNonce || "").trim();
    const challengeSig = String(body.challengeSignatureB64 || "").trim();

    if (!appCode || !deviceId) {
      return sendJson(res, 400, { ok: false, error: "appCode and deviceId are required" });
    }

    let signatureVerified = false;
    if (challengeNonce && challengeSig && keystorePubKey) {
      const nonceEntry = storage.state.nonces[challengeNonce];
      if (!nonceEntry) {
        return sendJson(res, 403, { ok: false, error: "Invalid or expired challenge nonce" });
      }
      delete storage.state.nonces[challengeNonce];
      if (!verifyDeviceChallengeSignature(keystorePubKey, challengeNonce, challengeSig)) {
        return sendJson(res, 403, { ok: false, error: "Keystore ECDSA P-256 signature verification failed" });
      }
      signatureVerified = true;
    }

    const app = storage.resolveIsolatedApp(appCode);
    if (!app) {
      return sendJson(res, 404, { ok: false, error: `Isolated app not found: ${appCode}` });
    }

    // Upsert user if userId supplied
    let userRecord = null;
    if (userId) {
      userRecord = storage.state.users.find((u) => u.uid === userId || u.id === userId);
      if (!userRecord) {
        userRecord = {
          id: userId,
          uid: userId,
          appId: app.id,
          email: String(body.userEmail || ""),
          displayName: String(body.userName || userId),
          status: "ACTIVE",
          banReason: "",
          userConfig: {},
          lastLoginAtMs: Date.now(),
          createdAtMs: Date.now(),
          updatedAtMs: Date.now()
        };
        storage.state.users.push(userRecord);
      } else {
        userRecord.lastLoginAtMs = Date.now();
        userRecord.updatedAtMs = Date.now();
      }
    }

    // Upsert device tracking isolated per appId
    let device = storage.state.devices.find((d) => d.installUuid === deviceId && d.appId === app.id);
    if (!device) {
      device = {
        id: `dev_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        appId: app.id,
        userUid: userId,
        deviceLabel: String(body.deviceModel || "Android Client"),
        installUuid: deviceId,
        keystoreKeyHash: keystoreHash,
        approvalStatus: "APPROVED",
        androidApi: Number(body.androidApi || 35),
        riskScore: 0,
        transferCount: 0,
        installedVersion: versionName,
        installedVersionCode: versionCode,
        lastSeenMs: Date.now()
      };
      storage.state.devices.push(device);
    } else {
      device.lastSeenMs = Date.now();
      if (userId) device.userUid = userId;
      if (keystoreHash && device.keystoreKeyHash && device.keystoreKeyHash !== keystoreHash) {
        device.riskScore = Math.min(100, Number(device.riskScore || 0) + 40);
      }
    }
    storage.save();

    if (storage.state.workspace.emergencyLockdown) {
      return sendJson(res, 200, {
        ok: true,
        allowed: false,
        lockdown: true,
        signatureVerified,
        dialogs: [{
          dialogType: "SECURITY_LOCK",
          title: "Security Lockdown Active",
          description: "Remote access has been temporarily locked by the Root Owner.",
          dismissible: false,
          laterAllowed: false
        }]
      });
    }

    if (userRecord && userRecord.status === "BANNED") {
      return sendJson(res, 403, {
        ok: false,
        allowed: false,
        userBanned: true,
        error: "User account banned by owner",
        dialogs: [{
          dialogType: "SECURITY_LOCK",
          title: "Account Restricted",
          description: userRecord.banReason || "Your account has been suspended by the administrator.",
          dismissible: false,
          laterAllowed: false
        }]
      });
    }

    const now = Date.now();
    const wsMaintStarts = Number(storage.state.workspace.maintenanceStartsAtMs || 0);
    const wsMaintEnds = Number(storage.state.workspace.maintenanceExpiresAtMs || 0);
    const wsMaintWindowActive = (!wsMaintStarts || now >= wsMaintStarts) && (!wsMaintEnds || now <= wsMaintEnds);
    const globalMaint = Boolean(storage.state.workspace.masterMaintenance) && wsMaintWindowActive;
    const appMaint = Boolean(app.maintenanceEnabled) || app.lifecycleStatus !== "ACTIVE" || !app.masterEnabled;

    if (globalMaint || appMaint) {
      return sendJson(res, 200, {
        ok: true,
        allowed: false,
        maintenance: true,
        signatureVerified,
        dialogs: [{
          dialogType: "MAINTENANCE",
          title: "Service Maintenance",
          description: (app.maintenanceEnabled && app.maintenanceMessage)
            ? app.maintenanceMessage
            : (storage.state.workspace.maintenanceMessage || "Application temporarily paused for maintenance."),
          dismissible: false,
          laterAllowed: false
        }]
      });
    }

    if (device.approvalStatus === "BLOCKED") {
      return sendJson(res, 403, {
        ok: false,
        allowed: false,
        error: "Device is blocked by owner policy",
        dialogs: [{
          dialogType: "SECURITY_LOCK",
          title: "Device Suspended",
          description: device.restrictions || "This device installation has been blocked.",
          dismissible: false,
          laterAllowed: false
        }]
      });
    }

    const updateScheduledOk = (!app.scheduledAtMs || now >= Number(app.scheduledAtMs))
      && (!app.expiresAtMs || now <= Number(app.expiresAtMs));
    const userTargetedOk = !Array.isArray(app.targetUserIds) || app.targetUserIds.length === 0
      || (userId && app.targetUserIds.includes(userId));
    const versionTargetedOk = !Array.isArray(app.targetVersions) || app.targetVersions.length === 0
      || app.targetVersions.includes(versionName);

    const belowMin = app.minSupportedVersion && compareSemver(versionName, app.minSupportedVersion) < 0;
    const hasNewer = app.latestVersion && compareSemver(versionName, app.latestVersion) < 0;
    const eligibleRollout = inRolloutBucket(deviceId, app.appCode, app.rolloutPercent);
    const shouldForce = Boolean(app.forceUpdateEnabled || belowMin) && updateScheduledOk && userTargetedOk;
    const shouldShowUpdate = Boolean(app.popupEnabled && hasNewer && eligibleRollout && updateScheduledOk && userTargetedOk && versionTargetedOk);

    const evalContext = { app, userId, deviceId, versionName, now };
    const dialogs = [...storage.getDialogsForApp(app.id, app.appCode, evalContext)];
    if (shouldForce || shouldShowUpdate) {
      dialogs.unshift({
        dialogType: shouldForce ? "FORCE_UPDATE" : "NORMAL_UPDATE",
        title: app.updateTitle || `Update ${app.displayName}`,
        description: app.updateDescription || `Version ${app.latestVersion} is ready.`,
        whatsNew: app.whatsNew || "",
        releaseNotes: app.releaseNotes || "",
        primaryButtonText: app.button1Text || "Update Now",
        primaryActionType: app.apkDownloadUrl && app.apkDownloadUrl.endsWith(".apk") ? "DOWNLOAD_APK" : "OPEN_URL",
        primaryActionUrl: app.apkDownloadUrl || "",
        secondaryButtonText: shouldForce ? "" : (app.button2Text || "Later"),
        forceUpdateEnabled: shouldForce,
        dismissible: !shouldForce,
        laterAllowed: shouldForce ? false : Boolean(app.laterEnabled),
        latestVersion: app.latestVersion,
        versionCode: Number(app.versionCode || 1)
      });
    }

    const activeLic = storage.state.licenses.find(
      (l) => (l.appId === app.id || l.appId === "global")
        && (l.boundDeviceId === deviceId || (Array.isArray(l.boundDeviceIds) && l.boundDeviceIds.includes(deviceId)))
        && l.status === "ACTIVE"
        && !isLicenseExpired(l, now)
    );

    const remoteConfig = storage.resolveRemoteConfig(evalContext);
    if (userRecord && userRecord.userConfig && Object.keys(userRecord.userConfig).length > 0) {
      Object.assign(remoteConfig.values, userRecord.userConfig);
    }
    const dynamicModules = storage.resolveDynamicModules(evalContext);
    const announcements = storage.getMatchingAnnouncements(evalContext);

    return sendJson(res, 200, {
      ok: true,
      allowed: true,
      signatureVerified,
      user: userRecord ? { uid: userRecord.uid, status: userRecord.status, displayName: userRecord.displayName } : null,
      app: {
        id: app.id,
        appCode: app.appCode,
        packageName: app.packageName,
        latestVersion: app.latestVersion,
        versionCode: Number(app.versionCode || 1),
        minSupportedVersion: app.minSupportedVersion,
        apkDownloadUrl: app.apkDownloadUrl || "",
        forceUpdateEnabled: shouldForce,
        laterEnabled: shouldForce ? false : Boolean(app.laterEnabled),
        rolloutEligible: eligibleRollout
      },
      license: activeLic ? {
        licenseKey: activeLic.licenseKey,
        planTier: activeLic.planTier,
        status: activeLic.status,
        paymentVerified: Boolean(activeLic.paymentVerified),
        expiresAtEpochMs: activeLic.expiresAtEpochMs,
        deviceLimit: Number(activeLic.deviceLimit || 1)
      } : null,
      remoteConfig,
      dynamicModules,
      announcements,
      dialogs
    });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/config") {
    const body = await readJsonBody(req);
    const appCode = String(body.appCode || body.packageName || "").trim();
    const app = appCode ? storage.resolveIsolatedApp(appCode) : null;
    const evalContext = {
      app,
      userId: String(body.userId || "").trim(),
      deviceId: String(body.deviceId || "").trim(),
      versionName: String(body.versionName || "1.0.0").trim()
    };
    const remoteConfig = storage.resolveRemoteConfig(evalContext);
    const dynamicModules = storage.resolveDynamicModules(evalContext);
    return sendJson(res, 200, { ok: true, remoteConfig, dynamicModules });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/license/activate") {
    const body = await readJsonBody(req);
    const key = String(body.licenseKey || "").trim();
    const deviceId = String(body.deviceId || body.installUuid || "").trim();
    const appCode = String(body.appCode || "").trim();
    const userId = String(body.userId || "").trim();
    if (!key || !deviceId) {
      return sendJson(res, 400, { ok: false, error: "licenseKey and deviceId are required" });
    }
    const app = appCode ? storage.resolveIsolatedApp(appCode) : null;
    const lic = storage.state.licenses.find((l) => l.licenseKey === key);
    if (!lic) return sendJson(res, 404, { ok: false, error: "License key not found" });

    if (lic.appId !== "global" && app && lic.appId !== app.id && lic.appId !== app.appCode) {
      return sendJson(res, 403, { ok: false, error: "License is isolated to a different application" });
    }
    if (lic.status === "REVOKED" || lic.status === "EXPIRED" || lic.status === "SUSPENDED" || isLicenseExpired(lic)) {
      return sendJson(res, 403, { ok: false, error: `License status is ${isLicenseExpired(lic) ? "EXPIRED" : lic.status}` });
    }
    if (String(lic.planTier || "").startsWith("VIP") && !lic.paymentVerified) {
      return sendJson(res, 402, { ok: false, error: "VIP entitlement requires verified payment before activation" });
    }

    const boundIds = Array.isArray(lic.boundDeviceIds)
      ? [...lic.boundDeviceIds]
      : (lic.boundDeviceId ? [lic.boundDeviceId] : []);
    const limit = Math.max(1, Number(lic.deviceLimit || 1));

    if (!boundIds.includes(deviceId) && boundIds.length >= limit) {
      return sendJson(res, 409, { ok: false, error: `License device limit (${limit}) already bound to another device` });
    }

    if (!boundIds.includes(deviceId)) boundIds.push(deviceId);
    lic.boundDeviceIds = boundIds;
    lic.boundDeviceId = lic.boundDeviceId || deviceId;
    if (userId && !lic.userUid) lic.userUid = userId;
    lic.status = "ACTIVE";
    lic.activatedAtMs = lic.activatedAtMs || Date.now();
    if (!lic.expiresAtEpochMs) {
      lic.expiresAtEpochMs = Date.now() + Number(lic.durationDays || 30) * 86_400_000;
    }
    lic.history = Array.isArray(lic.history) ? lic.history : [];
    lic.history.unshift({ action: "ACTIVATED", deviceId, atMs: Date.now() });
    storage.save();
    return sendJson(res, 200, {
      ok: true,
      status: lic.status,
      planTier: lic.planTier,
      boundDeviceId: lic.boundDeviceId,
      boundDeviceIds: lic.boundDeviceIds,
      expiresAtEpochMs: lic.expiresAtEpochMs
    });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/license/verify") {
    const body = await readJsonBody(req);
    const key = String(body.licenseKey || "").trim();
    const deviceId = String(body.deviceId || "").trim();
    const appCode = String(body.appCode || "").trim();
    const app = appCode ? storage.resolveIsolatedApp(appCode) : null;
    const lic = storage.state.licenses.find(
      (l) => (key ? l.licenseKey === key : (l.boundDeviceId === deviceId || (Array.isArray(l.boundDeviceIds) && l.boundDeviceIds.includes(deviceId))))
    );
    if (!lic) return sendJson(res, 404, { ok: false, valid: false, error: "No matching license" });
    if (lic.appId !== "global" && app && lic.appId !== app.id && lic.appId !== app.appCode) {
      return sendJson(res, 403, { ok: false, valid: false, error: "App isolation mismatch" });
    }
    const expired = isLicenseExpired(lic);
    const boundIds = Array.isArray(lic.boundDeviceIds) ? lic.boundDeviceIds : (lic.boundDeviceId ? [lic.boundDeviceId] : []);
    const deviceOk = !deviceId || boundIds.includes(deviceId);
    const valid = lic.status === "ACTIVE" && !expired && deviceOk && (!String(lic.planTier).startsWith("VIP") || Boolean(lic.paymentVerified));
    return sendJson(res, valid ? 200 : 403, {
      ok: valid,
      valid,
      status: expired ? "EXPIRED" : lic.status,
      planTier: lic.planTier,
      isVip: String(lic.planTier || "").startsWith("VIP") && valid,
      expiresAtEpochMs: lic.expiresAtEpochMs
    });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/license/transfer") {
    const body = await readJsonBody(req);
    const key = String(body.licenseKey || "").trim();
    const newDeviceId = String(body.newDeviceId || "").trim();
    const lic = storage.state.licenses.find((l) => l.licenseKey === key);
    if (!lic || !newDeviceId) return sendJson(res, 404, { ok: false, error: "License or newDeviceId missing" });

    const transferCount = Number(lic.transferCount || 0);
    if (transferCount >= config.maxLicenseTransfers) {
      return sendJson(res, 429, { ok: false, error: "Maximum device transfers exceeded" });
    }
    const cooldownHours = Number(lic.transferCooldownHours ?? config.defaultCooldownHours);
    if (lic.lastTransferAtMs && Date.now() - Number(lic.lastTransferAtMs) < cooldownHours * 3600000) {
      return sendJson(res, 429, { ok: false, error: `Transfer cooldown (${cooldownHours}h) active` });
    }

    lic.boundDeviceId = newDeviceId;
    lic.boundDeviceIds = [newDeviceId];
    lic.transferCount = transferCount + 1;
    lic.lastTransferAtMs = Date.now();
    lic.history = Array.isArray(lic.history) ? lic.history : [];
    lic.history.unshift({ action: "TRANSFERRED", deviceId: newDeviceId, atMs: Date.now() });
    storage.save();
    return sendJson(res, 200, { ok: true, boundDeviceId: newDeviceId, transferCount: lic.transferCount });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/update-confirm") {
    const body = await readJsonBody(req);
    const appCode = String(body.appCode || "").trim();
    const deviceId = String(body.deviceId || "").trim();
    const installedVersion = String(body.installedVersion || "").trim();
    if (!appCode || !deviceId || !installedVersion) {
      return sendJson(res, 400, { ok: false, error: "appCode, deviceId, and installedVersion are required" });
    }
    const app = storage.resolveIsolatedApp(appCode);
    if (!app) return sendJson(res, 404, { ok: false, error: "App not found" });

    const dev = storage.state.devices.find((d) => d.installUuid === deviceId && d.appId === app.id);
    if (dev) {
      dev.installedVersion = installedVersion;
      dev.updateConfirmedAtMs = Date.now();
    }
    storage.state.updateConfirmations.unshift({
      appId: app.id,
      appCode: app.appCode,
      deviceId,
      installedVersion,
      confirmedAtMs: Date.now()
    });
    storage.save();
    return sendJson(res, 200, { ok: true, installedVersion });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/announcements/ack") {
    const body = await readJsonBody(req);
    const announcementId = String(body.announcementId || "").trim();
    const deviceId = String(body.deviceId || "").trim();
    const userId = String(body.userId || "").trim();
    const dismissed = Boolean(body.dismissed);
    const ann = storage.state.announcements.find((a) => a.id === announcementId);
    if (!ann) return sendJson(res, 404, { ok: false, error: "Announcement not found" });
    ann.readCount = Number(ann.readCount || 0) + 1;
    if (dismissed && (deviceId || userId)) {
      ann.dismissedBy = Array.isArray(ann.dismissedBy) ? ann.dismissedBy : [];
      const key = `${deviceId}:${userId}`;
      if (!ann.dismissedBy.includes(key)) ann.dismissedBy.push(key);
      if (deviceId && !ann.dismissedBy.includes(deviceId)) ann.dismissedBy.push(deviceId);
    }
    storage.save();
    return sendJson(res, 200, { ok: true, readCount: ann.readCount });
  }

  if (req.method === "POST" && pathname === "/api/v1/client/events") {
    const body = await readJsonBody(req);
    const evt = storage.appendEvent(
      String(body.category || "SDK"),
      String(body.title || "SDK Event"),
      String(body.message || ""),
      String(body.severity || "INFO"),
      String(body.appCode || body.appId || "global"),
      { userId: String(body.userId || ""), deviceId: String(body.deviceId || "") }
    );
    return sendJson(res, 200, { ok: true, eventId: evt.id });
  }

  return false;
}
