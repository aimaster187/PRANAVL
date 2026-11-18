import { readJsonBody, requireOwnerAuth, sendJson } from "../httpHelpers.js";

function toStringArray(v) {
  if (Array.isArray(v)) return v.map((x) => String(x).trim()).filter(Boolean);
  if (typeof v === "string") return v.split(",").map((x) => x.trim()).filter(Boolean);
  return [];
}

function parseObject(val, fallback = {}) {
  if (val && typeof val === "object" && !Array.isArray(val)) return val;
  if (typeof val === "string" && val.trim()) {
    try {
      const parsed = JSON.parse(val);
      if (parsed && typeof parsed === "object") return parsed;
    } catch {}
  }
  return fallback;
}

export async function handleAdminRoutes(req, res, pathname, { config, storage }) {
  if (!pathname.startsWith("/api/v1/admin")) return false;
  requireOwnerAuth(req, config);

  if (req.method === "GET" && pathname === "/api/v1/admin/overview") {
    return sendJson(res, 200, {
      ok: true,
      workspace: storage.state.workspace,
      counts: {
        apps: storage.state.apps.length,
        users: storage.state.users.length,
        dialogs: storage.state.dialogs.length,
        devices: storage.state.devices.length,
        licenses: storage.state.licenses.length,
        announcements: storage.state.announcements.length,
        remoteConfigs: storage.state.remoteConfigs.length,
        dynamicModules: storage.state.dynamicModules.length,
        bots: storage.state.bots.length,
        events: storage.state.events.length,
        auditLogs: storage.state.auditLogs.length
      }
    });
  }

  // ---- APPS & UPDATES ----
  if (req.method === "GET" && pathname === "/api/v1/admin/apps") {
    return sendJson(res, 200, { ok: true, apps: storage.state.apps });
  }

  if (req.method === "GET" && pathname === "/api/v1/admin/updates/history") {
    return sendJson(res, 200, { ok: true, updates: storage.state.updateConfirmations });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/apps") {
    const body = await readJsonBody(req);
    const id = body.id || `app_${Date.now()}`;
    const forceUpdate = Boolean(body.forceUpdateEnabled);
    const app = {
      id,
      appCode: String(body.appCode || id),
      packageName: String(body.packageName || "com.pranav.client"),
      displayName: String(body.displayName || "Managed App"),
      versionName: String(body.versionName || "1.0.0"),
      versionCode: Number(body.versionCode || 100),
      lifecycleStatus: String(body.lifecycleStatus || "ACTIVE"),
      masterEnabled: body.masterEnabled !== false,
      popupEnabled: Boolean(body.popupEnabled),
      forceUpdateEnabled: forceUpdate,
      laterEnabled: forceUpdate ? false : body.laterEnabled !== false,
      latestVersion: String(body.latestVersion || "1.0.0"),
      minSupportedVersion: String(body.minSupportedVersion || "1.0.0"),
      apkDownloadUrl: String(body.apkDownloadUrl || ""),
      updateTitle: String(body.updateTitle || "Update Available"),
      updateDescription: String(body.updateDescription || ""),
      releaseNotes: String(body.releaseNotes || ""),
      whatsNew: String(body.whatsNew || ""),
      channelLink: String(body.channelLink || ""),
      button1Text: String(body.button1Text || "Update Now"),
      button2Text: String(body.button2Text || "Later"),
      rolloutPercent: Math.max(0, Math.min(100, Number(body.rolloutPercent ?? 100))),
      scheduledAtMs: Number(body.scheduledAtMs || 0),
      expiresAtMs: Number(body.expiresAtMs || 0),
      targetUserIds: toStringArray(body.targetUserIds),
      targetVersions: toStringArray(body.targetVersions || body.targetVersion),
      maintenanceEnabled: Boolean(body.maintenanceEnabled),
      maintenanceMessage: String(body.maintenanceMessage || ""),
      updatedAtMs: Date.now()
    };
    const idx = storage.state.apps.findIndex((a) => a.id === id || a.appCode === app.appCode);
    if (idx >= 0) storage.state.apps[idx] = app;
    else storage.state.apps.push(app);
    storage.appendAuditLog({
      action: idx >= 0 ? "UPDATE_APP" : "CREATE_APP",
      targetType: "APP",
      targetId: app.id,
      summary: `${app.displayName} (${app.packageName}) v${app.latestVersion} force=${app.forceUpdateEnabled}`
    });
    return sendJson(res, 200, { ok: true, app });
  }

  const deleteAppMatch = pathname.match(/^\/api\/v1\/admin\/apps\/([^/]+)$/);
  if (req.method === "DELETE" && deleteAppMatch) {
    const targetId = decodeURIComponent(deleteAppMatch[1]);
    storage.state.apps = storage.state.apps.filter((a) => a.id !== targetId && a.appCode !== targetId);
    storage.appendAuditLog({ action: "DELETE_APP", targetType: "APP", targetId, summary: `Deleted app ${targetId}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- USERS ----
  if (req.method === "GET" && pathname === "/api/v1/admin/users") {
    return sendJson(res, 200, { ok: true, users: storage.state.users });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/users") {
    const body = await readJsonBody(req);
    const uid = String(body.uid || body.id || "").trim();
    if (!uid) return sendJson(res, 400, { ok: false, error: "uid is required" });
    const idx = storage.state.users.findIndex((u) => u.uid === uid || u.id === uid);
    const prev = idx >= 0 ? storage.state.users[idx] : {};
    const user = {
      ...prev,
      id: prev.id || uid,
      uid,
      appId: String(body.appId || prev.appId || "global"),
      email: String(body.email || prev.email || ""),
      displayName: String(body.displayName || prev.displayName || uid),
      status: String(body.status || prev.status || "ACTIVE"),
      banReason: String(body.banReason ?? prev.banReason ?? ""),
      userConfig: parseObject(body.userConfig ?? body.userConfigJson, prev.userConfig || {}),
      note: String(body.note ?? prev.note ?? ""),
      lastLoginAtMs: Number(body.lastLoginAtMs || prev.lastLoginAtMs || Date.now()),
      createdAtMs: prev.createdAtMs || Date.now(),
      updatedAtMs: Date.now()
    };
    if (idx >= 0) storage.state.users[idx] = user;
    else storage.state.users.push(user);
    storage.appendAuditLog({
      action: idx >= 0 ? "UPDATE_USER" : "CREATE_USER",
      targetType: "USER",
      targetId: uid,
      summary: `User ${user.displayName} (${user.status})`
    });
    return sendJson(res, 200, { ok: true, user });
  }

  const userBanMatch = pathname.match(/^\/api\/v1\/admin\/users\/([^/]+)\/ban$/);
  if (req.method === "POST" && userBanMatch) {
    const uid = decodeURIComponent(userBanMatch[1]);
    const body = await readJsonBody(req);
    const banned = Boolean(body.banned);
    const reason = String(body.reason || "");
    let user = storage.state.users.find((u) => u.uid === uid || u.id === uid);
    if (!user) {
      user = { id: uid, uid, appId: "global", email: "", displayName: uid, userConfig: {}, createdAtMs: Date.now() };
      storage.state.users.push(user);
    }
    user.status = banned ? "BANNED" : "ACTIVE";
    user.banReason = banned ? (reason || "Restricted by Root Owner") : "";
    user.updatedAtMs = Date.now();
    storage.appendAuditLog({
      action: banned ? "BAN_USER" : "UNBAN_USER",
      targetType: "USER",
      targetId: uid,
      summary: `${banned ? "Banned" : "Unbanned"} user ${uid}: ${user.banReason}`
    });
    return sendJson(res, 200, { ok: true, user });
  }

  const userActivityMatch = pathname.match(/^\/api\/v1\/admin\/users\/([^/]+)\/activity$/);
  if (req.method === "GET" && userActivityMatch) {
    const uid = decodeURIComponent(userActivityMatch[1]);
    const logs = storage.state.events.filter((e) => e.userId === uid);
    return sendJson(res, 200, { ok: true, uid, events: logs });
  }

  const deleteUserMatch = pathname.match(/^\/api\/v1\/admin\/users\/([^/]+)$/);
  if (req.method === "DELETE" && deleteUserMatch) {
    const uid = decodeURIComponent(deleteUserMatch[1]);
    storage.state.users = storage.state.users.filter((u) => u.uid !== uid && u.id !== uid);
    storage.appendAuditLog({ action: "DELETE_USER", targetType: "USER", targetId: uid, summary: `Deleted user ${uid}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- DIALOGS ----
  if (req.method === "GET" && pathname === "/api/v1/admin/dialogs") {
    return sendJson(res, 200, { ok: true, dialogs: storage.state.dialogs });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/dialogs") {
    const body = await readJsonBody(req);
    const id = body.id || `dlg_${Date.now()}`;
    const forceDialog = Boolean(body.forceDialog || body.dialogType === "FORCE_UPDATE" || body.dialogType === "SECURITY_LOCK");
    const dlg = {
      id,
      appId: String(body.appId || "global"),
      dialogType: String(body.dialogType || "NORMAL_UPDATE"),
      title: String(body.title || "Notice"),
      description: String(body.description || ""),
      primaryButtonText: String(body.primaryButtonText || "Continue"),
      primaryActionType: String(body.primaryActionType || "OPEN_URL"),
      primaryActionUrl: String(body.primaryActionUrl || ""),
      secondaryButtonText: String(body.secondaryButtonText || "Later"),
      secondaryActionType: String(body.secondaryActionType || "DISMISS"),
      animationStyle: String(body.animationStyle || "GLASS_SLIDE"),
      soundName: String(body.soundName || "DEFAULT_CHIME"),
      forceDialog,
      dismissible: forceDialog ? false : body.dismissible !== false,
      laterAllowed: forceDialog ? false : body.laterAllowed !== false,
      enabled: body.enabled !== false,
      priority: Number(body.priority || 10),
      targetUserIds: toStringArray(body.targetUserIds),
      targetDeviceIds: toStringArray(body.targetDeviceIds),
      targetVersions: toStringArray(body.targetVersions),
      startsAtMs: Number(body.startsAtMs || 0),
      expiresAtMs: Number(body.expiresAtMs || 0),
      updatedAtMs: Date.now()
    };
    const idx = storage.state.dialogs.findIndex((d) => d.id === id);
    if (idx >= 0) storage.state.dialogs[idx] = dlg;
    else storage.state.dialogs.push(dlg);
    storage.appendAuditLog({
      action: idx >= 0 ? "UPDATE_DIALOG" : "CREATE_DIALOG",
      targetType: "DIALOG",
      targetId: dlg.id,
      summary: `${dlg.dialogType}: ${dlg.title}`
    });
    return sendJson(res, 200, { ok: true, dialog: dlg });
  }

  const deleteDialogMatch = pathname.match(/^\/api\/v1\/admin\/dialogs\/([^/]+)$/);
  if (req.method === "DELETE" && deleteDialogMatch) {
    const id = decodeURIComponent(deleteDialogMatch[1]);
    storage.state.dialogs = storage.state.dialogs.filter((d) => d.id !== id);
    storage.appendAuditLog({ action: "DELETE_DIALOG", targetType: "DIALOG", targetId: id, summary: `Deleted dialog ${id}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- DEVICES ----
  if (req.method === "GET" && pathname === "/api/v1/admin/devices") {
    return sendJson(res, 200, { ok: true, devices: storage.state.devices });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/devices") {
    const body = await readJsonBody(req);
    const id = body.id || `dev_${Date.now()}`;
    const installUuid = String(body.installUuid || "");
    const existing = storage.state.devices.find(
      (d) => d.id === id || (installUuid && d.installUuid === installUuid && d.appId === body.appId)
    );
    const dev = {
      ...(existing || {}),
      id: existing?.id || id,
      appId: String(body.appId || existing?.appId || "global"),
      userUid: String(body.userUid ?? existing?.userUid ?? ""),
      deviceLabel: String(body.deviceLabel || existing?.deviceLabel || "Android Device"),
      installUuid: installUuid || existing?.installUuid || id,
      keystoreKeyHash: String(body.keystoreKeyHash || existing?.keystoreKeyHash || ""),
      approvalStatus: String(body.approvalStatus || existing?.approvalStatus || "PENDING"),
      restrictions: String(body.restrictions ?? existing?.restrictions ?? ""),
      androidApi: Number(body.androidApi || existing?.androidApi || 35),
      riskScore: Number(body.riskScore ?? existing?.riskScore ?? 0),
      transferCount: Number(body.transferCount ?? existing?.transferCount ?? 0),
      updatedAtMs: Date.now()
    };
    const idx = storage.state.devices.findIndex((d) => d.id === dev.id);
    if (idx >= 0) storage.state.devices[idx] = dev;
    else storage.state.devices.push(dev);
    storage.appendAuditLog({
      action: idx >= 0 ? "UPDATE_DEVICE" : "REGISTER_DEVICE",
      targetType: "DEVICE",
      targetId: dev.id,
      summary: `${dev.deviceLabel} -> ${dev.approvalStatus}`
    });
    return sendJson(res, 200, { ok: true, device: dev });
  }

  const deleteDeviceMatch = pathname.match(/^\/api\/v1\/admin\/devices\/([^/]+)$/);
  if (req.method === "DELETE" && deleteDeviceMatch) {
    const id = decodeURIComponent(deleteDeviceMatch[1]);
    storage.state.devices = storage.state.devices.filter((d) => d.id !== id && d.installUuid !== id);
    storage.appendAuditLog({ action: "DELETE_DEVICE", targetType: "DEVICE", targetId: id, summary: `Removed device ${id}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- LICENSES & VIP ----
  if (req.method === "GET" && pathname === "/api/v1/admin/licenses") {
    return sendJson(res, 200, { ok: true, licenses: storage.state.licenses });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/licenses") {
    const body = await readJsonBody(req);
    const id = body.id || `lic_${Date.now()}`;
    const existingIdx = storage.state.licenses.findIndex(
      (l) => l.id === id || (body.licenseKey && l.licenseKey === body.licenseKey)
    );
    const prev = existingIdx >= 0 ? storage.state.licenses[existingIdx] : {};
    const boundList = Array.isArray(body.boundDeviceIds)
      ? body.boundDeviceIds
      : (Array.isArray(prev.boundDeviceIds) ? prev.boundDeviceIds : []);
    const boundSingle = String(body.boundDeviceId ?? prev.boundDeviceId ?? "");
    if (boundSingle && !boundList.includes(boundSingle)) boundList.push(boundSingle);

    const history = Array.isArray(prev.history) ? [...prev.history] : [];
    const nextStatus = String(body.status || prev.status || "UNUSED");
    if (existingIdx < 0) {
      history.unshift({ action: "CREATED", status: nextStatus, atMs: Date.now() });
    } else if (prev.status !== nextStatus) {
      history.unshift({ action: `STATUS_${nextStatus}`, status: nextStatus, atMs: Date.now() });
    }

    const lic = {
      ...prev,
      id: prev.id || id,
      appId: String(body.appId || prev.appId || "global"),
      userUid: String(body.userUid ?? prev.userUid ?? ""),
      licenseKey: String(body.licenseKey || prev.licenseKey || `PRNV-${Date.now()}`),
      planTier: String(body.planTier || prev.planTier || "VIP_PRO"),
      status: nextStatus,
      durationDays: Number(body.durationDays ?? prev.durationDays ?? 30),
      graceDays: Number(body.graceDays ?? prev.graceDays ?? 3),
      deviceLimit: Math.max(1, Number(body.deviceLimit ?? prev.deviceLimit ?? 1)),
      paymentVerified: body.paymentVerified !== undefined ? Boolean(body.paymentVerified) : Boolean(prev.paymentVerified),
      transferCooldownHours: Number(body.transferCooldownHours ?? prev.transferCooldownHours ?? config.defaultCooldownHours),
      boundDeviceId: boundSingle,
      boundDeviceIds: boundList,
      expiresAtEpochMs: Number(body.expiresAtEpochMs ?? prev.expiresAtEpochMs ?? 0),
      transferCount: Number(body.transferCount ?? prev.transferCount ?? 0),
      history: history.slice(0, 50)
    };
    if (existingIdx >= 0) storage.state.licenses[existingIdx] = lic;
    else storage.state.licenses.push(lic);
    storage.appendAuditLog({
      action: existingIdx >= 0 ? "UPDATE_LICENSE" : "ISSUE_LICENSE",
      targetType: "LICENSE",
      targetId: lic.id,
      summary: `${lic.licenseKey} (${lic.planTier}) -> ${lic.status}`
    });
    return sendJson(res, 200, { ok: true, license: lic });
  }

  const deleteLicMatch = pathname.match(/^\/api\/v1\/admin\/licenses\/([^/]+)$/);
  if (req.method === "DELETE" && deleteLicMatch) {
    const id = decodeURIComponent(deleteLicMatch[1]);
    storage.state.licenses = storage.state.licenses.filter((l) => l.id !== id && l.licenseKey !== id);
    storage.appendAuditLog({ action: "DELETE_LICENSE", targetType: "LICENSE", targetId: id, summary: `Deleted license ${id}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- ANNOUNCEMENTS ----
  if (req.method === "GET" && pathname === "/api/v1/admin/announcements") {
    return sendJson(res, 200, { ok: true, announcements: storage.state.announcements });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/announcements") {
    const body = await readJsonBody(req);
    const id = body.id || `ann_${Date.now()}`;
    const prev = storage.state.announcements.find((a) => a.id === id) || {};
    const ann = {
      ...prev,
      id,
      scope: String(body.scope || prev.scope || "GLOBAL"), // GLOBAL, APP, USER, DEVICE
      displayType: String(body.displayType || prev.displayType || "POPUP"), // POPUP, BANNER, TOAST, NOTIFICATION
      appId: String(body.appId || prev.appId || "global"),
      targetUserIds: toStringArray(body.targetUserIds ?? body.targetUserId),
      targetDeviceIds: toStringArray(body.targetDeviceIds ?? body.targetDeviceId),
      title: String(body.title || "Announcement"),
      message: String(body.message || ""),
      actionText: String(body.actionText || "OK"),
      actionUrl: String(body.actionUrl || ""),
      priority: Number(body.priority ?? 10),
      dismissible: body.dismissible !== false,
      enabled: body.enabled !== false,
      startsAtMs: Number(body.startsAtMs || 0),
      expiresAtMs: Number(body.expiresAtMs || 0),
      readCount: Number(prev.readCount || 0),
      dismissedBy: Array.isArray(prev.dismissedBy) ? prev.dismissedBy : [],
      updatedAtMs: Date.now()
    };
    const idx = storage.state.announcements.findIndex((a) => a.id === id);
    if (idx >= 0) storage.state.announcements[idx] = ann;
    else storage.state.announcements.unshift(ann);
    storage.appendAuditLog({
      action: idx >= 0 ? "UPDATE_ANNOUNCEMENT" : "PUBLISH_ANNOUNCEMENT",
      targetType: "ANNOUNCEMENT",
      targetId: ann.id,
      summary: `[${ann.scope}/${ann.displayType}] ${ann.title}`
    });
    return sendJson(res, 200, { ok: true, announcement: ann });
  }

  const deleteAnnMatch = pathname.match(/^\/api\/v1\/admin\/announcements\/([^/]+)$/);
  if (req.method === "DELETE" && deleteAnnMatch) {
    const id = decodeURIComponent(deleteAnnMatch[1]);
    storage.state.announcements = storage.state.announcements.filter((a) => a.id !== id);
    storage.appendAuditLog({ action: "DELETE_ANNOUNCEMENT", targetType: "ANNOUNCEMENT", targetId: id, summary: `Deleted announcement ${id}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- REMOTE CONFIG ENGINE ----
  if (req.method === "GET" && pathname === "/api/v1/admin/configs") {
    return sendJson(res, 200, { ok: true, configs: storage.state.remoteConfigs });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/configs") {
    const body = await readJsonBody(req);
    const configKey = String(body.configKey || "").trim();
    if (!configKey) return sendJson(res, 400, { ok: false, error: "configKey is required" });
    const id = body.id || `cfg_${configKey}_${Date.now()}`;
    const idx = storage.state.remoteConfigs.findIndex((c) => c.id === id);
    const prev = idx >= 0 ? storage.state.remoteConfigs[idx] : {};
    const nextVersion = Number(body.version || (Number(prev.version || 0) + 1));
    const values = parseObject(body.values ?? body.payloadJson, {});
    const featureFlags = parseObject(body.featureFlags ?? body.featureFlagsJson, {});
    const history = Array.isArray(prev.history) ? [...prev.history] : [];
    if (idx >= 0) {
      history.unshift({
        version: prev.version,
        values: prev.values,
        featureFlags: prev.featureFlags,
        savedAtMs: Date.now()
      });
    }
    const cfg = {
      ...prev,
      id,
      appId: String(body.appId || prev.appId || "global"),
      configKey,
      version: nextVersion,
      status: String(body.status || prev.status || "PUBLISHED"),
      enabled: body.enabled !== false,
      priority: Number(body.priority ?? prev.priority ?? 10),
      values,
      featureFlags,
      targetUserIds: toStringArray(body.targetUserIds),
      targetDeviceIds: toStringArray(body.targetDeviceIds),
      targetVersions: toStringArray(body.targetVersions),
      startsAtMs: Number(body.startsAtMs || 0),
      expiresAtMs: Number(body.expiresAtMs || 0),
      history: history.slice(0, 20),
      updatedAtMs: Date.now()
    };
    if (idx >= 0) storage.state.remoteConfigs[idx] = cfg;
    else storage.state.remoteConfigs.unshift(cfg);
    storage.appendAuditLog({
      action: "SAVE_REMOTE_CONFIG",
      targetType: "REMOTE_CONFIG",
      targetId: cfg.id,
      summary: `Config ${cfg.configKey} v${cfg.version} (${cfg.status})`
    });
    return sendJson(res, 200, { ok: true, config: cfg });
  }

  const cfgRollbackMatch = pathname.match(/^\/api\/v1\/admin\/configs\/([^/]+)\/rollback$/);
  if (req.method === "POST" && cfgRollbackMatch) {
    const id = decodeURIComponent(cfgRollbackMatch[1]);
    const cfg = storage.state.remoteConfigs.find((c) => c.id === id);
    if (!cfg) return sendJson(res, 404, { ok: false, error: "Remote config not found" });
    if (!Array.isArray(cfg.history) || cfg.history.length === 0) {
      return sendJson(res, 400, { ok: false, error: "No previous version available to rollback" });
    }
    const previous = cfg.history.shift();
    cfg.version = Number(cfg.version || 1) + 1;
    cfg.values = previous.values || {};
    cfg.featureFlags = previous.featureFlags || {};
    cfg.status = "PUBLISHED";
    cfg.updatedAtMs = Date.now();
    storage.appendAuditLog({
      action: "ROLLBACK_REMOTE_CONFIG",
      targetType: "REMOTE_CONFIG",
      targetId: cfg.id,
      summary: `Rolled back config ${cfg.configKey} -> v${cfg.version}`
    });
    return sendJson(res, 200, { ok: true, config: cfg });
  }

  const deleteCfgMatch = pathname.match(/^\/api\/v1\/admin\/configs\/([^/]+)$/);
  if (req.method === "DELETE" && deleteCfgMatch) {
    const id = decodeURIComponent(deleteCfgMatch[1]);
    storage.state.remoteConfigs = storage.state.remoteConfigs.filter((c) => c.id !== id);
    storage.appendAuditLog({ action: "DELETE_REMOTE_CONFIG", targetType: "REMOTE_CONFIG", targetId: id, summary: `Deleted config ${id}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- DYNAMIC MODULES ENGINE ----
  if (req.method === "GET" && pathname === "/api/v1/admin/modules") {
    return sendJson(res, 200, { ok: true, modules: storage.state.dynamicModules });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/modules") {
    const body = await readJsonBody(req);
    const moduleKey = String(body.moduleKey || "").trim();
    if (!moduleKey) return sendJson(res, 400, { ok: false, error: "moduleKey is required" });
    const id = body.id || `mod_${moduleKey}_${Date.now()}`;
    const idx = storage.state.dynamicModules.findIndex((m) => m.id === id);
    const prev = idx >= 0 ? storage.state.dynamicModules[idx] : {};
    const history = Array.isArray(prev.history) ? [...prev.history] : [];
    if (idx >= 0) {
      history.unshift({
        version: prev.version,
        defaultValues: prev.defaultValues,
        actionParams: prev.actionParams,
        savedAtMs: Date.now()
      });
    }
    const mod = {
      ...prev,
      id,
      appId: String(body.appId || prev.appId || "global"),
      moduleKey,
      moduleName: String(body.moduleName || moduleKey),
      category: String(body.category || "Dynamic"),
      description: String(body.description || ""),
      enabled: body.enabled !== false,
      status: String(body.status || "PUBLISHED"),
      version: Number(body.version || (Number(prev.version || 0) + 1)),
      fieldsSchema: parseObject(body.fieldsSchema ?? body.fieldsSchemaJson, {}),
      defaultValues: parseObject(body.defaultValues ?? body.defaultValuesJson, {}),
      actionParams: parseObject(body.actionParams ?? body.actionParamsJson, {}),
      targetUserIds: toStringArray(body.targetUserIds),
      targetDeviceIds: toStringArray(body.targetDeviceIds),
      targetVersions: toStringArray(body.targetVersions),
      priority: Number(body.priority ?? 10),
      history: history.slice(0, 20),
      updatedAtMs: Date.now()
    };
    if (idx >= 0) storage.state.dynamicModules[idx] = mod;
    else storage.state.dynamicModules.unshift(mod);
    storage.appendAuditLog({
      action: "SAVE_DYNAMIC_MODULE",
      targetType: "DYNAMIC_MODULE",
      targetId: mod.id,
      summary: `Module ${mod.moduleKey} v${mod.version} enabled=${mod.enabled}`
    });
    return sendJson(res, 200, { ok: true, module: mod });
  }

  const modRollbackMatch = pathname.match(/^\/api\/v1\/admin\/modules\/([^/]+)\/rollback$/);
  if (req.method === "POST" && modRollbackMatch) {
    const id = decodeURIComponent(modRollbackMatch[1]);
    const mod = storage.state.dynamicModules.find((m) => m.id === id);
    if (!mod) return sendJson(res, 404, { ok: false, error: "Dynamic module not found" });
    if (!Array.isArray(mod.history) || mod.history.length === 0) {
      return sendJson(res, 400, { ok: false, error: "No previous module revision to rollback" });
    }
    const prev = mod.history.shift();
    mod.version = Number(mod.version || 1) + 1;
    mod.defaultValues = prev.defaultValues || {};
    mod.actionParams = prev.actionParams || {};
    mod.status = "PUBLISHED";
    mod.updatedAtMs = Date.now();
    storage.appendAuditLog({
      action: "ROLLBACK_DYNAMIC_MODULE",
      targetType: "DYNAMIC_MODULE",
      targetId: mod.id,
      summary: `Rolled back module ${mod.moduleKey} -> v${mod.version}`
    });
    return sendJson(res, 200, { ok: true, module: mod });
  }

  const deleteModMatch = pathname.match(/^\/api\/v1\/admin\/modules\/([^/]+)$/);
  if (req.method === "DELETE" && deleteModMatch) {
    const id = decodeURIComponent(deleteModMatch[1]);
    storage.state.dynamicModules = storage.state.dynamicModules.filter((m) => m.id !== id);
    storage.appendAuditLog({ action: "DELETE_DYNAMIC_MODULE", targetType: "DYNAMIC_MODULE", targetId: id, summary: `Deleted module ${id}` });
    return sendJson(res, 200, { ok: true, status: "DELETED" });
  }

  // ---- WORKSPACE, EVENTS & AUDIT LOGS ----
  if (req.method === "POST" && pathname === "/api/v1/admin/workspace") {
    const body = await readJsonBody(req);
    storage.state.workspace = {
      ...storage.state.workspace,
      masterMaintenance: Boolean(body.masterMaintenance),
      maintenanceMessage: String(body.maintenanceMessage || storage.state.workspace.maintenanceMessage),
      maintenanceStartsAtMs: Number(body.maintenanceStartsAtMs ?? storage.state.workspace.maintenanceStartsAtMs ?? 0),
      maintenanceExpiresAtMs: Number(body.maintenanceExpiresAtMs ?? storage.state.workspace.maintenanceExpiresAtMs ?? 0),
      emergencyLockdown: Boolean(body.emergencyLockdown)
    };
    storage.appendAuditLog({
      action: "UPDATE_WORKSPACE",
      targetType: "WORKSPACE",
      targetId: "primary",
      summary: `Maintenance=${storage.state.workspace.masterMaintenance}, Lockdown=${storage.state.workspace.emergencyLockdown}`
    });
    return sendJson(res, 200, { ok: true, workspace: storage.state.workspace });
  }

  if (req.method === "POST" && pathname === "/api/v1/admin/events") {
    const body = await readJsonBody(req);
    const evt = storage.appendEvent(
      String(body.category || "OPS"),
      String(body.title || "Admin Event"),
      String(body.message || ""),
      String(body.severity || "INFO"),
      String(body.appId || "global")
    );
    return sendJson(res, 200, { ok: true, event: evt });
  }

  const resolveEvtMatch = pathname.match(/^\/api\/v1\/admin\/events\/([^/]+)\/resolve$/);
  if (req.method === "POST" && resolveEvtMatch) {
    const evtId = decodeURIComponent(resolveEvtMatch[1]);
    const evt = storage.state.events.find((e) => e.id === evtId);
    if (evt) {
      evt.status = "RESOLVED";
      evt.resolvedAtMs = Date.now();
    }
    storage.appendAuditLog({
      action: "RESOLVE_EVENT",
      targetType: "EVENT",
      targetId: evtId,
      summary: `Resolved event ${evtId}`
    });
    return sendJson(res, 200, { ok: true, status: "RESOLVED" });
  }

  if (req.method === "GET" && pathname === "/api/v1/admin/audit-logs") {
    return sendJson(res, 200, { ok: true, logs: storage.state.auditLogs });
  }

  return false;
}
