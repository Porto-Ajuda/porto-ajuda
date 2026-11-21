import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado } from "./api.js";
import { CAUSE_IDS, causeCategoriesForNgo, normalizeCausePreferences } from "./cause-taxonomy.js";
import { baselineWeights, normalizeWeights, transferVisitWeight, weightedNgoOrder } from "./recommendation-model.js";
import { alreadyLearnedVisit, eligibleVisitTarget, profileMembershipExcludesNgo } from "./recommendation-policy.js";

const API = getAPI_URL();
const CONSENT_KEY = "portoAjudaPersonalizacaoConsent:v1";
const ACTIVE_IDENTITY_KEY = "portoAjudaPersonalizacaoIdentity:v1";
const LEGACY_PREFERENCES_KEY = "portoAjudaPreferencias";
const EXPLICIT_CAUSES_PREFIX = "portoAjudaCausasSalvas:v1:";
const STORAGE_PREFIX = "portoAjudaRecomendacoes:v1:";
const VISIT_TTL_DAYS = 90;
const MAX_VISITS = 500;

let memoryConsent = null;
let memoryPreferences = [];
let memoryIdentity = null;
let memoryState = null;
let initPromise = null;
let membershipPromise = null;
let membershipResult = null;

function storage() {
  try {
    const key = "__pa_recommendation_storage_test__";
    localStorage.setItem(key, "1"); localStorage.removeItem(key);
    return localStorage;
  } catch { return null; }
}
function readJson(key, fallback = null) {
  try { return JSON.parse(storage()?.getItem(key) ?? "null") ?? fallback; } catch { return fallback; }
}
function writeJson(key, value) {
  try { storage()?.setItem(key, JSON.stringify(value)); return Boolean(storage()); } catch { return false; }
}
function removeKey(key) { try { storage()?.removeItem(key); } catch { /* private browsing / quota denial */ } }
function todayLocal() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}-${String(now.getDate()).padStart(2,"0")}`;
}
function dayAge(day) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(day || ""))) return Infinity;
  const [year, month, date] = day.split("-").map(Number);
  const then = new Date(year, month-1, date).getTime();
  return Math.floor((new Date().setHours(0,0,0,0) - then) / 86400000);
}
function identityDataKey(identity) { return `${STORAGE_PREFIX}${identity || "anonymous"}`; }
function consentRecord() {
  const stored = readJson(CONSENT_KEY);
  return stored && typeof stored === "object" ? stored : memoryConsent;
}
function saveConsent(next) { memoryConsent = next; writeJson(CONSENT_KEY, next); }
function explicitCausesKey(identity) { return `${EXPLICIT_CAUSES_PREFIX}${identity}`; }
function currentData() {
  if (!memoryIdentity) return null;
  const stored = readJson(identityDataKey(memoryIdentity), null);
  if (stored && typeof stored === "object") memoryState = sanitizeState(stored);
  if (!memoryState) memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
  return memoryState;
}
function sanitizeState(raw) {
  const weights = normalizeWeights(raw.weights || baselineWeights(memoryPreferences));
  const visits = (Array.isArray(raw.visits) ? raw.visits : [])
    .filter(item => Number.isInteger(Number(item?.ngoId)) && dayAge(item?.day) >= 0 && dayAge(item?.day) < VISIT_TTL_DAYS)
    .slice(-MAX_VISITS)
    .map(item => ({ ngoId: Number(item.ngoId), day: item.day }));
  return { weights, visits };
}
function persistLearnedState() {
  const consent = consentRecord();
  if (!memoryIdentity || !consent?.accepted || consent?.enabled !== true || !memoryState) return false;
  return writeJson(identityDataKey(memoryIdentity), memoryState);
}
function clearIdentityData(identity) { if (identity) removeKey(identityDataKey(identity)); }
function clearAllLearnedData() {
  clearIdentityData(memoryIdentity);
  clearIdentityData("anonymous");
  try {
    const store = storage();
    if (store) for (const key of Object.keys(store)) if (key.startsWith(STORAGE_PREFIX)) store.removeItem(key);
  } catch { /* storage unavailable */ }
}

async function resolveIdentityAndPreferences() {
  let resolved = "anonymous";
  try {
    const response = await fetch(`${API}/usuario/sessao-atual`, { credentials: "include", cache: "no-store", headers: { Accept: "application/json" } });
    if (response.ok) {
      const session = await response.json();
      if (session?.autenticado === true && /^\d+$/.test(String(session.usuario || ""))) resolved = String(session.usuario);
    }
  } catch { /* no session data is stored or sent for analytics */ }

  const previous = (() => { try { return storage()?.getItem(ACTIVE_IDENTITY_KEY); } catch { return null; } })();
  if (previous && previous !== resolved) {
    clearIdentityData(previous);
    clearIdentityData(resolved);
    clearIdentityData("anonymous");
    memoryState = null;
  }
  memoryIdentity = resolved;
  try { storage()?.setItem(ACTIVE_IDENTITY_KEY, resolved); } catch { /* transient identity only */ }

  memoryPreferences = resolved === "anonymous"
    ? []
    : normalizeCausePreferences(readJson(explicitCausesKey(resolved), []));
  if (resolved !== "anonymous") {
    try {
      const response = await fetchAutenticado("/usuario/preferencias", { method: "GET" });
      if (response.ok) {
        const preferences = await response.json();
        if (preferences?.causasPersistidas === true && Array.isArray(preferences.tiposOng)) {
          const remote = normalizeCausePreferences(preferences.tiposOng);
          if (remote.length || !memoryPreferences.length) memoryPreferences = remote;
          writeJson(explicitCausesKey(resolved), memoryPreferences);
        }
      }
    } catch { /* retain explicitly saved, same-browser profile preferences */ }
  }
  memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
  const consent = consentRecord();
  if (consent?.accepted && consent?.enabled === true) {
    const stored = readJson(identityDataKey(resolved), null);
    if (stored) memoryState = sanitizeState(stored);
  }
  return { identity: resolved, causes: [...memoryPreferences], consent: consentRecord(), weights: getWeights() };
}

export function ready() {
  if (!initPromise) initPromise = resolveIdentityAndPreferences().catch(() => {
    memoryIdentity = memoryIdentity || "anonymous";
    memoryPreferences = memoryIdentity === "anonymous" ? [] : normalizeCausePreferences(readJson(explicitCausesKey(memoryIdentity), []));
    memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
    return { identity: memoryIdentity, causes: [...memoryPreferences], consent: consentRecord(), weights: getWeights() };
  });
  return initPromise;
}

export function getWeights() {
  const base = baselineWeights(memoryPreferences);
  const consent = consentRecord();
  if (!consent?.accepted || consent?.enabled !== true) return base;
  return normalizeWeights(currentData()?.weights || base);
}
export function getSavedCausePreferences() { return [...memoryPreferences]; }
export function isPersonalizationEnabled() {
  const consent = consentRecord();
  return consent?.accepted === true && consent?.enabled === true;
}

export async function saveExplicitCausePreferences(values) {
  await ready();
  const normalized = normalizeCausePreferences(values);
  memoryPreferences = normalized;
  if (memoryIdentity && memoryIdentity !== "anonymous") writeJson(explicitCausesKey(memoryIdentity), normalized);
  memoryState = { weights: baselineWeights(normalized), visits: [] };
  persistLearnedState();
  window.dispatchEvent(new CustomEvent("portoajuda:causas-explicitas-salvas", { detail: { tiposOng: [...normalized] } }));
  return [...normalized];
}

export function resetSuggestions() {
  memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
  if (memoryIdentity) {
    if (isPersonalizationEnabled()) writeJson(identityDataKey(memoryIdentity), memoryState);
    else clearIdentityData(memoryIdentity);
  }
  window.dispatchEvent(new CustomEvent("portoajuda:sugestoes-redefinidas"));
  return getWeights();
}

export function setPersonalizationEnabled(enabled, { rejected = false } = {}) {
  if (enabled) saveConsent({ accepted: true, enabled: true, updatedAt: Date.now() });
  else {
    saveConsent({ accepted: rejected ? false : consentRecord()?.accepted === true, enabled: false, updatedAt: Date.now() });
    clearAllLearnedData();
    memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
  }
  if (enabled) {
    memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
    persistLearnedState();
  }
  updateConsentUi();
  window.dispatchEvent(new CustomEvent("portoajuda:personalizacao-alterada", { detail: { enabled: Boolean(enabled) } }));
  return isPersonalizationEnabled();
}

export function acceptPersonalization() { return setPersonalizationEnabled(true); }
export function rejectPersonalization() { return setPersonalizationEnabled(false, { rejected: true }); }

async function resolveMemberships() {
  await ready();
  if (memoryIdentity === "anonymous") return { ok: true, ids: new Set() };
  if (!membershipPromise) membershipPromise = (async () => {
    try {
      const response = await fetchAutenticado("/osc/minhas", { method: "GET" });
      if (!response.ok) return { ok: false, ids: new Set() };
      const organizations = await response.json();
      if (!Array.isArray(organizations)) return { ok: false, ids: new Set() };
      return { ok: true, ids: new Set(organizations.map(item => Number(item?.id)).filter(Number.isInteger)) };
    } catch { return { ok: false, ids: new Set() }; }
  })();
  membershipResult = await membershipPromise;
  return membershipResult;
}

export async function recordEligibleProfileVisit(ngo) {
  await ready();
  if (!isPersonalizationEnabled() || !eligibleVisitTarget(ngo)) return false;
  const ngoId = Number(ngo.id);
  if (!Number.isSafeInteger(ngoId) || ngoId <= 0) return false;
  const membership = await resolveMemberships();
  if (!membership.ok || profileMembershipExcludesNgo([...membership.ids].map(id => ({ id, cargo: "MEMBRO" })), ngoId)) return false;
  const causes = causeCategoriesForNgo(ngo);
  if (!causes.length) return false;
  const state = currentData();
  if (!state) return false;
  const day = todayLocal();
  if (alreadyLearnedVisit(state.visits, ngoId, day)) return false;
  state.weights = transferVisitWeight(state.weights, causes);
  state.visits = [...state.visits.filter(item => dayAge(item.day) < VISIT_TTL_DAYS), { ngoId, day }].slice(-MAX_VISITS);
  persistLearnedState();
  window.dispatchEvent(new CustomEvent("portoajuda:pesos-recomendacao-atualizados"));
  return true;
}

export function orderNgoRecommendations(ngos, random = Math.random) {
  return weightedNgoOrder(ngos, getWeights(), random);
}

function createConsentUi() {
  if (document.getElementById("pa-personalization-consent")) return;
  const style = document.createElement("link");
  style.rel = "stylesheet"; style.href = "/styles/personalization.css"; document.head.append(style);
  const banner = document.createElement("section");
  banner.id = "pa-personalization-consent"; banner.className = "pa-personalization-consent";
  banner.setAttribute("role", "region"); banner.setAttribute("aria-labelledby", "pa-personalization-title"); banner.hidden = true;
  banner.innerHTML = `<div class="pa-personalization-consent__copy"><strong id="pa-personalization-title">Sugestões personalizadas</strong><p>Se aceitar, o Porto Ajuda usa neste navegador as visitas a perfis públicos de ONGs para ordenar sugestões. Não registramos impressões de cards nem enviamos pesos ou histórico ao servidor. As causas salvas no perfil funcionam independentemente desta opção.</p><p class="pa-personalization-consent__accessibility">Acessibilidade e preferências visuais não dependem desta escolha.</p></div><div class="pa-personalization-consent__actions"><button type="button" data-pa-consent="accept">Aceitar</button><button type="button" data-pa-consent="reject">Rejeitar personalização</button><button type="button" data-pa-consent="settings">Configurações</button></div>`;
  const dialog = document.createElement("dialog");
  dialog.id = "pa-personalization-settings"; dialog.className = "pa-personalization-settings";
  dialog.setAttribute("aria-labelledby", "pa-personalization-settings-title");
  dialog.innerHTML = `<form method="dialog"><h2 id="pa-personalization-settings-title">Personalização pelas minhas visitas</h2><p>Somente perfis públicos de ONGs carregados com sucesso contam. O histórico fica neste navegador, por até 90 dias, e nunca é enviado ao backend. Organizações em que você tem qualquer vínculo não contam.</p><label><input type="checkbox" id="pa-personalization-switch"> Permitir personalização pelas minhas visitas</label><p class="pa-personalization-settings__note">Suas causas salvas no perfil e os recursos de acessibilidade continuam disponíveis mesmo com esta opção desligada.</p><div class="pa-personalization-consent__actions"><button type="button" data-pa-consent="reject">Rejeitar opcional</button><button type="button" value="cancel">Cancelar</button><button type="button" id="pa-personalization-save">Salvar escolha</button></div></form>`;
  document.body.prepend(banner);
  document.body.append(dialog);
  banner.querySelector('[data-pa-consent="accept"]').addEventListener("click", acceptPersonalization);
  banner.querySelector('[data-pa-consent="reject"]').addEventListener("click", rejectPersonalization);
  banner.querySelector('[data-pa-consent="settings"]').addEventListener("click", openPersonalizationSettings);
  dialog.querySelector('[data-pa-consent="reject"]').addEventListener("click", () => { rejectPersonalization(); dialog.close(); });
  dialog.querySelector("#pa-personalization-save").addEventListener("click", () => {
    if (dialog.querySelector("#pa-personalization-switch").checked) acceptPersonalization();
    else rejectPersonalization();
    dialog.close();
  });
  window.addEventListener("portoajuda:personalizacao-alterada", updateConsentUi);
  window.addEventListener("portoajuda:causas-explicitas-salvas", () => {
    const reset = document.getElementById("pa-recommendation-reset-status");
    if (reset) reset.textContent = "As sugestões foram recalculadas a partir das causas salvas.";
  });
  updateConsentUi();
}

export function openPersonalizationSettings() {
  const dialog = document.getElementById("pa-personalization-settings");
  if (!dialog) return;
  const choice = dialog.querySelector("#pa-personalization-switch");
  choice.checked = isPersonalizationEnabled();
  if (typeof dialog.showModal === "function") dialog.showModal();
  else dialog.setAttribute("open", "");
}

function updateConsentUi() {
  if (typeof document === "undefined") return;
  const banner = document.getElementById("pa-personalization-consent");
  if (banner) banner.hidden = Boolean(consentRecord());
  const profileSwitch = document.getElementById("personalizar-pelas-minhas-visitas");
  if (profileSwitch) profileSwitch.checked = isPersonalizationEnabled();
}

function clearActiveIdentity() {
  clearIdentityData(memoryIdentity);
  clearIdentityData("anonymous");
  try { storage()?.setItem(ACTIVE_IDENTITY_KEY, "anonymous"); } catch { /* in-memory fallback */ }
  memoryIdentity = "anonymous";
  memoryPreferences = [];
  memoryState = { weights: baselineWeights(memoryPreferences), visits: [] };
  membershipPromise = null; membershipResult = null;
  initPromise = Promise.resolve({ identity: "anonymous", causes: [], consent: consentRecord(), weights: getWeights() });
}

function refreshIdentityAfterAccountChange() {
  const previous = memoryIdentity;
  clearIdentityData(previous);
  clearIdentityData("anonymous");
  memoryState = null;
  memoryPreferences = [];
  membershipPromise = null; membershipResult = null;
  initPromise = null;
  void ready();
}

if (typeof window !== "undefined") {
  if (typeof document !== "undefined") {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", createConsentUi, { once: true });
    else createConsentUi();
  }
  window.addEventListener("portoajuda:sessao-anonima", clearActiveIdentity);
  window.addEventListener("portoajuda:sessao-autenticada", refreshIdentityAfterAccountChange);
  window.addEventListener("storage", event => {
    if (event.key === "porto-ajuda-estado-sessao" && event.newValue === "anonymous") clearActiveIdentity();
    if (event.key === "porto-ajuda-estado-sessao" && event.newValue === "authenticated") refreshIdentityAfterAccountChange();
  });
}
