import fs from "node:fs";
import path from "node:path";

const DEFAULT_STATE = () => ({
  workspace: {
    masterMaintenance: false,
    maintenanceMessage: "Scheduled maintenance in progress.",
    maintenanceStartsAtMs: 0,
    maintenanceExpiresAtMs: 0,
    emergencyLockdown: false
  },
  apps: [],
  users: [],
  dialogs: [],
  devices: [],
  licenses: [],
  announcements: [],
  remoteConfigs: [],
  dynamicModules: [],
  bots: [],
  vault: {},
  events: [],
  auditLogs: [],
  updateConfirmations: [],
  nonces: {}
});

function isWindowActive(item, now = Date.now()) {
  const start = Number(item?.startsAtMs || item?.scheduledAtMs || 0);
  const end = Number(item?.expiresAtMs || 0);
  if (start > 0 && now < start) return false;
  if (end > 0 && now > end) return false;
  return true;
}

function matchesTargets(item, { app, userId, deviceId, versionName }) {
  if (item.appId && item.appId !== "global" && app) {
    if (item.appId !== app.id && item.appId !== app.appCode && item.appId !== app.packageName) {
      return false;
    }
  }
  const targetUsers = Array.isArray(item.targetUserIds)
    ? item.targetUserIds
    : (item.targetUserId ? [item.targetUserId] : []);
  if (targetUsers.length > 0 && (!userId || !targetUsers.includes(userId))) {
    return false;
  }
  const targetDevices = Array.isArray(item.targetDeviceIds)
    ? item.targetDeviceIds
    : (item.targetDeviceId ? [item.targetDeviceId] : []);
  if (targetDevices.length > 0 && (!deviceId || !targetDevices.includes(deviceId))) {
    return false;
  }
  const targetVersions = Array.isArray(item.targetVersions)
    ? item.targetVersions
    : (item.targetVersion ? [item.targetVersion] : []);
  if (targetVersions.length > 0 && (!versionName || !targetVersions.includes(versionName))) {
    return false;
  }
  return true;
}

export class PranavStorage {
  constructor(dataDir) {
    this.dataDir = path.resolve(dataDir);
    this.dbFile = path.join(this.dataDir, "pranav_store.json");
    fs.mkdirSync(this.dataDir, { recursive: true });
    this.state = this._load();
  }

  _load() {
    if (!fs.existsSync(this.dbFile)) {
      const init = DEFAULT_STATE();
      this._persist(init);
      return init;
    }
    try {
      const parsed = JSON.parse(fs.readFileSync(this.dbFile, "utf8"));
      return { ...DEFAULT_STATE(), ...parsed };
    } catch {
      const fallback = DEFAULT_STATE();
      this._persist(fallback);
      return fallback;
    }
  }

  _persist(nextState = this.state) {
    const tmp = `${this.dbFile}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(nextState, null, 2), "utf8");
    fs.renameSync(tmp, this.dbFile);
  }

  save() {
    this._persist(this.state);
  }

  resolveIsolatedApp(appIdentifier) {
    return this.state.apps.find(
      (a) => a.id === appIdentifier || a.appCode === appIdentifier || a.packageName === appIdentifier
    );
  }

  getDialogsForApp(appId, appCode, context = {}) {
    const app = context.app || this.resolveIsolatedApp(appId || appCode);
    const now = context.now || Date.now();
    return this.state.dialogs
      .filter((d) => {
        if (d.enabled === false) return false;
        if (!isWindowActive(d, now)) return false;
        return matchesTargets(d, {
          app,
          userId: context.userId,
          deviceId: context.deviceId,
          versionName: context.versionName
        });
      })
      .sort((a, b) => Number(b.priority || 0) - Number(a.priority || 0));
  }

  getMatchingAnnouncements(context = {}) {
    const now = context.now || Date.now();
    const ackKey = `${context.deviceId || ""}:${context.userId || ""}`;
    return this.state.announcements
      .filter((a) => {
        if (a.enabled === false) return false;
        if (!isWindowActive(a, now)) return false;
        if (Array.isArray(a.dismissedBy) && (a.dismissedBy.includes(context.deviceId) || a.dismissedBy.includes(ackKey))) {
          return false;
        }
        return matchesTargets(a, context);
      })
      .sort((a, b) => Number(b.priority || 0) - Number(a.priority || 0));
  }

  resolveRemoteConfig(context = {}) {
    const now = context.now || Date.now();
    const matching = this.state.remoteConfigs
      .filter((c) => {
        if (c.status !== "PUBLISHED" || c.enabled === false) return false;
        if (!isWindowActive(c, now)) return false;
        return matchesTargets(c, context);
      })
      .sort((a, b) => Number(a.priority || 0) - Number(b.priority || 0));

    const mergedValues = {};
    const mergedFlags = {};
    let highestVersion = 0;

    for (const cfg of matching) {
      highestVersion = Math.max(highestVersion, Number(cfg.version || 1));
      if (cfg.values && typeof cfg.values === "object") {
        Object.assign(mergedValues, cfg.values);
      }
      if (cfg.featureFlags && typeof cfg.featureFlags === "object") {
        Object.assign(mergedFlags, cfg.featureFlags);
      }
    }

    return {
      configVersion: highestVersion,
      configCount: matching.length,
      values: mergedValues,
      featureFlags: mergedFlags,
      ttlSeconds: 300
    };
  }

  resolveDynamicModules(context = {}) {
    const now = context.now || Date.now();
    return this.state.dynamicModules
      .filter((m) => {
        if (m.status !== "PUBLISHED" || m.enabled === false) return false;
        if (!isWindowActive(m, now)) return false;
        return matchesTargets(m, context);
      })
      .sort((a, b) => Number(b.priority || 0) - Number(a.priority || 0))
      .map((m) => ({
        id: m.id,
        moduleKey: m.moduleKey,
        moduleName: m.moduleName,
        category: m.category,
        version: m.version,
        values: { ...(m.defaultValues || {}), ...(m.actionParams || {}) }
      }));
  }

  appendEvent(category, title, message, severity = "INFO", appId = "global", meta = {}) {
    const evt = {
      id: `evt_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      appId,
      category,
      title,
      message,
      severity,
      status: "OPEN",
      userId: meta.userId || "",
      deviceId: meta.deviceId || "",
      createdAtMs: Date.now()
    };
    this.state.events.unshift(evt);
    if (this.state.events.length > 500) {
      this.state.events.length = 500;
    }
    this.save();
    return evt;
  }

  appendAuditLog({ actor = "owner", action, targetType, targetId = "", summary = "", metadata = {} }) {
    const entry = {
      id: `aud_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      actor,
      action,
      targetType,
      targetId,
      summary,
      metadata,
      createdAtMs: Date.now()
    };
    this.state.auditLogs.unshift(entry);
    if (this.state.auditLogs.length > 1000) {
      this.state.auditLogs.length = 1000;
    }
    this.save();
    return entry;
  }
}
