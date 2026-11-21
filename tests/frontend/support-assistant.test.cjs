const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");

class Element {
  constructor(tag = "div", id = "") {
    this.tagName = tag.toUpperCase(); this.id = id; this.children = []; this.listeners = {};
    this.dataset = {};
    this.attributes = new Map(); this.hidden = false; this.disabled = false; this.value = "";
    this.textContent = ""; this.className = ""; this.open = false; this.scrollHeight = 0;
    this.classList = { toggle: () => {}, add: () => {}, remove: () => {} };
  }
  get childElementCount() { return this.children.filter(child => child instanceof Element).length; }
  addEventListener(name, handler) { (this.listeners[name] ||= []).push(handler); }
  async dispatch(name, event = {}) {
    let result;
    for (const handler of this.listeners[name] || []) result = await handler({ target: this, preventDefault() {}, stopPropagation() {}, ...event });
    return result;
  }
  setAttribute(name, value) { this.attributes.set(name, String(value)); }
  getAttribute(name) { return this.attributes.get(name) ?? null; }
  append(...nodes) { this.children.push(...nodes); }
  appendChild(node) { this.append(node); return node; }
  replaceChildren(...nodes) { this.children = [...nodes]; }
  dispatchEvent(event) { return this.dispatch(event.type, event); }
  focus() { this.ownerDocument.activeElement = this; }
  show() { this.open = true; }
  close() { this.open = false; void this.dispatch("close"); }
  contains(node) { return node === this || this.children.some(child => child?.contains?.(node)); }
}

function makeDocument() {
  const elements = new Map();
  const document = {
    activeElement: null,
    listeners: {},
    createElement(tag) { const element = new Element(tag); element.ownerDocument = document; return element; },
    createTextNode(text) { return { textContent: String(text) }; },
    getElementById(id) { return elements.get(id) || null; },
    addEventListener(name, handler) { (this.listeners[name] ||= []).push(handler); }
  };
  document.body = new Element("body"); document.body.ownerDocument = document;
  document.body.insertAdjacentHTML = (_position, html) => {
    for (const match of html.matchAll(/<([a-z0-9]+)([^>]*)>/gi)) {
      const id = match[2].match(/\bid="([^"]+)"/)?.[1];
      if (!id) continue;
      const element = new Element(match[1], id); element.ownerDocument = document;
      element.hidden = /\shidden(?:\s|=|>)/i.test(match[0]);
      elements.set(element.id, element);
    }
  };
  return document;
}

const response = payload => ({ status: 200, ok: true, json: async () => payload });
const textContent = node => node?.children?.length
  ? node.children.map(textContent).join("")
  : String(node?.textContent || "");
const baseTime = "2026-11-15T09:00:00Z";
const message = (id, senderType, body) => ({ id, senderType, senderLabel: senderType, body, createdAt: baseTime });
const conversation = (id, body) => ({
  id, state: "AI_ACTIVE", assignedStaffLabel: null, createdAt: baseTime, updatedAt: baseTime,
  closedAt: null, contentExpiresAt: null, version: 2, unread: false,
  messages: [message(1, "SYSTEM", "Olá! Sou a Sophie."), message(2, "USER", body)],
  transitions: []
});

async function mountAssistant(fetchAutenticado) {
  const document = makeDocument();
  const source = (await readFile(resolve(__dirname, "../../public/components/support-assistant.js"), "utf8"))
    .replace('import { fetchAutenticado } from "./api.js";', "")
    .replace('import { createSupportVoiceController, DEFAULT_VOICE_PREFERENCE, normalizeVoicePreference, readVoicePreference, writeVoicePreference } from "./support-voice.js";', `
      const DEFAULT_VOICE_PREFERENCE = { enabled:false, readAloud:false, avoidScreenReaderAnnouncements:true, rate:1, voiceURI:"" };
      const normalizeVoicePreference = value => ({...DEFAULT_VOICE_PREFERENCE,...value});
      const readVoicePreference = (storage,key) => { try { const value=storage.getItem(key); return value?JSON.parse(value):null; } catch { return null; } };
      const writeVoicePreference = (storage,key,value) => { try { storage.setItem(key,JSON.stringify(value)); return true; } catch { return false; } };
      const createSupportVoiceController = ({onStatus}) => ({supported:false,synthesisSupported:false,dictating:false,speaking:false,
        startDictation(){onStatus("Ditado simulado indisponível.");return false;},stopDictation(){},speak(){return false;},stopSpeaking(){},voices(){return [];} });`);
  const localStorage = { values:new Map(), getItem(key){return this.values.get(key)||null;}, setItem(key,value){this.values.set(key,String(value));} };
  const voiceMock = { supported:true,synthesisSupported:true,dictating:false,speaking:false,
    startDictation(){this.dictating=true;this.onDictation?.(true);this.onStatus?.("Ditado ativo");this.onTranscript?.("texto ditado sintético");return true;},
    stopDictation(){this.dictating=false;}, speak(){this.speaking=true;return true;},stopSpeaking(){this.speaking=false;},voices(){return [];} };
  document.__voice = voiceMock;
  const sourceWithVoice = source.replace('const createSupportVoiceController = ({onStatus}) => ({supported:false,synthesisSupported:false,dictating:false,speaking:false,\n        startDictation(){onStatus("Ditado simulado indisponível.");return false;},stopDictation(){},speak(){return false;},stopSpeaking(){},voices(){return [];} });',
      'const createSupportVoiceController = hooks => { Object.assign(document.__voice, hooks); return document.__voice; };');
  vm.runInNewContext(sourceWithVoice, {
    document, window: { setInterval: () => 1, clearInterval() {}, setTimeout, clearTimeout, localStorage },
    fetchAutenticado, crypto: webcrypto, Intl, Date, URL, URLSearchParams,
    console, setTimeout, clearTimeout, encodeURIComponent, Event: class { constructor(type){this.type=type;} }
  });
  return document;
}

test("renders descriptive public links and refuses paths outside the allowlist", async () => {
  const id="44444444-4444-4444-8444-444444444444";
  const details={...conversation(id,"Quais ONGs ajudam animais?"),messages:[
    message(1,"SYSTEM","Olá! Sou a Sophie."),
    message(2,"USER","Quais ONGs ajudam animais?"),
    message(3,"AI","Encontrei duas opções relacionadas.\n• Ver perfil de [Abrigo Sintético](/ong-profile?id=782201) — causa animal.\n[Filtro animal](/catalogo?categoria=ongs&filtro=ANIMAIS)\n[Link externo](https://example.invalid)\n[Home](/)")
  ]};
  details.messages[2].body += "\n[Vaquinha de teste](/catalogo?categoria=doacoes&campanha=31)";
  const fetchAutenticado=async path=>{
    if(path==="/support-assistant/intro")return response({assistantName:"Sophie",conversationsEnabled:true,aiAvailable:true});
    if(path==="/support-assistant/conversations")return response([{id,state:"AI_ACTIVE",unread:false,firstUserMessage:"Quais ONGs ajudam animais?",lastMessagePreview:"Encontrei duas opções."}]);
    if(path===`/support-assistant/conversations/${id}`)return response(details);
    if(path==="/support-assistant/voice-preference-scope")return response({userId:700004});
    throw new Error("Unexpected path while checking rendered links.");
  };
  const document=await mountAssistant(fetchAutenticado);
  await document.getElementById("support-assistant-launcher").dispatch("click");
  for(let i=0;i<20&&document.getElementById("support-message-stream").children.length===0;i++)await new Promise(resolve=>setImmediate(resolve));
  const stream=document.getElementById("support-message-stream");
  const anchors=[]; const walk=node=>{if(node?.tagName==="A")anchors.push(node); for(const child of node?.children||[])walk(child);}; walk(stream);
  assert.equal(anchors.length,3);
  assert.equal(anchors[0].textContent,"Abrigo Sintético");
  assert.equal(anchors[0].href,"/ong-profile?id=782201");
  assert.equal(anchors[1].textContent,"Filtro animal");
  assert.equal(anchors[2].textContent,"Vaquinha de teste");
  assert.equal(anchors[2].href,"/catalogo?categoria=doacoes&campanha=31");
  assert.doesNotMatch(textContent(stream),/example\\.invalid/);
  assert.doesNotMatch(textContent(stream),/Abrir p\\u00e1gina relacionada/);
});
test("histórico alterna abrir/fechar, marca a conversa selecionada e carrega somente o item escolhido", async () => {
  const firstId = "11111111-1111-4111-8111-111111111111";
  const secondId = "22222222-2222-4222-8222-222222222222";
  const summaries = [
    { id: firstId, state: "CLOSED", createdAt: baseTime, updatedAt: baseTime, unread: false,
      firstUserMessage: "Quero encontrar ONGs de leitura", lastMessagePreview: "Conversa encerrada" },
    { id: secondId, state: "WAITING_HUMAN", createdAt: baseTime, updatedAt: baseTime, unread: true,
      firstUserMessage: "Preciso de ajuda com voluntariado", lastMessagePreview: "Aguardando a equipe" }
  ];
  const calls = [];
  const fetchAutenticado = async (path, options = {}) => {
    calls.push({ path, options });
    if (path === "/support-assistant/intro") return response({
      assistantName: "Sophie", conversationsEnabled: true, aiAvailable: true
    });
    if (path === "/support-assistant/conversations") return response(summaries);
    if (path === "/support-assistant/voice-preference-scope") return response({ userId: 700001 });
    if (path.endsWith("/volunteer-application-drafts")) return response([]);
    if (path === "/support-assistant/conversations/" + firstId) return response({
      ...conversation(firstId, "Quero encontrar ONGs de leitura"), state: "CLOSED", closedAt: baseTime
    });
    if (path === "/support-assistant/conversations/" + secondId) return response({
      ...conversation(secondId, "Preciso de ajuda com voluntariado"), state: "WAITING_HUMAN", unread: false
    });
    throw new Error("Unexpected support API path in isolated UI test.");
  };
  const document=await mountAssistant(fetchAutenticado);

  const launcher = document.getElementById("support-assistant-launcher");
  await launcher.dispatch("click");
  for (let i = 0; i < 20 && !document.getElementById("support-history-items").children.length; i++)
    await new Promise(resolve => setImmediate(resolve));
  assert.match(textContent(document.getElementById("support-intro")),
    /Olá! Sou a Sophie, assistente virtual do Porto Ajuda\. Como posso ajudar\?/);

  const panel = document.getElementById("support-conversation-list");
  const toggle = document.getElementById("support-history");
  const items = document.getElementById("support-history-items");
  assert.equal(panel.hidden, true, "History is collapsed initially.");
  await toggle.dispatch("click");
  assert.equal(panel.hidden, false);
  assert.equal(toggle.getAttribute("aria-expanded"), "true");
  assert.equal(items.children.length, 2);

  const first = items.children[0];
  const second = items.children[1];
  assert.match(first.children[0].textContent, /Quero encontrar ONGs de leitura/);
  assert.match(first.children[1].textContent, /15\/11\/2026/);
  assert.equal(first.getAttribute("aria-pressed"), "true");
  assert.match(first.children[2].textContent, /Encerrada/);
  assert.equal(first.children[3].textContent, "Quero encontrar ONGs de leitura");
  assert.equal(second.getAttribute("aria-pressed"), "false");
  assert.match(second.children[2].textContent, /não lida/);

  const detailCallsBefore = calls.filter(call => /^\/support-assistant\/conversations\/[0-9a-f-]{36}$/.test(call.path)).length;
  await second.dispatch("click");
  assert.equal(panel.hidden, true, "Selecting a conversation closes history.");
  assert.equal(toggle.getAttribute("aria-expanded"), "false");
  const detailCallsAfter = calls.filter(call => /^\/support-assistant\/conversations\/[0-9a-f-]{36}$/.test(call.path));
  assert.equal(detailCallsAfter.length, detailCallsBefore + 1);
  assert.equal(detailCallsAfter.at(-1).path, "/support-assistant/conversations/" + secondId);
  assert.equal(document.getElementById("support-history-items").children[1].getAttribute("aria-pressed"), "true");
  assert.equal(document.getElementById("support-resume-ai").hidden, false,
    "An authenticated owner can explicitly return a waiting conversation to Sophie when AI is available.");
  assert.match(textContent(document.getElementById("support-message-stream")),
    /Preciso de ajuda com voluntariado/);
  assert.doesNotMatch(textContent(document.getElementById("support-message-stream")),
    /Quero encontrar ONGs de leitura/);

  await toggle.dispatch("click");
  assert.equal(panel.hidden, false);
  await document.getElementById("support-history-close").dispatch("click");
  assert.equal(panel.hidden, true);
  assert.equal(toggle.getAttribute("aria-expanded"), "false");
  assert.equal(document.activeElement, toggle, "The explicit close control restores focus to History.");
});

test("visitor sees the greeting but cannot create a persistent conversation before login", async () => {
  const calls=[];
  const fetchAutenticado=async path=>{
    calls.push(path);
    if(path==="/support-assistant/intro") return response({
      assistantName:"Sophie",conversationsEnabled:true,aiAvailable:false
    });
    if(path==="/support-assistant/conversations") return {status:401,ok:false,json:async()=>({})};
    throw new Error("Guest must not request private conversation details.");
  };
  const document=await mountAssistant(fetchAutenticado);
  await document.getElementById("support-assistant-launcher").dispatch("click");
  for(let i=0;i<20&&document.getElementById("support-login").hidden;i++)
    await new Promise(resolve=>setImmediate(resolve));
  assert.match(textContent(document.getElementById("support-intro")),
    /Olá! Sou a Sophie, assistente virtual do Porto Ajuda\. Como posso ajudar\?/);
  assert.equal(document.getElementById("support-login").hidden,false);
  assert.equal(document.getElementById("support-message").disabled,true);
  assert.equal(document.getElementById("support-new").hidden,true);
  assert.deepEqual(calls,["/support-assistant/intro","/support-assistant/conversations"]);
});

test("protected user and NGO registration forms stay outside chat messages", async () => {
  const calls=[];
  const fetchAutenticado=async (path,options={})=>{
    calls.push({path,options});
    if(path==="/support-assistant/intro")return response({assistantName:"Sophie",conversationsEnabled:true,aiAvailable:false});
    if(path==="/support-assistant/conversations")return response([]);
    if(path==="/support-assistant/voice-preference-scope")return response({userId:700003});
    throw new Error("Unexpected API call while showing protected signup forms.");
  };
  const document=await mountAssistant(fetchAutenticado);
  await document.getElementById("support-assistant-launcher").dispatch("click");
  const toggle=document.getElementById("support-secure-forms-toggle");
  await toggle.dispatch("click");
  assert.equal(document.getElementById("support-messages").hidden,true);
  await document.getElementById("support-form-user").dispatch("click");
  const frame=document.getElementById("support-secure-forms-frame");
  assert.equal(frame.src,"/login#cadastro");
  assert.match(document.getElementById("support-secure-forms-notice").textContent,/Senhas, documentos e códigos não passam pela conversa/);
  assert.equal(calls.some(call=>call.path.endsWith("/messages")),false);
  await document.getElementById("support-secure-forms-close").dispatch("click");
  await toggle.dispatch("click");
  await document.getElementById("support-form-ngo").dispatch("click");
  assert.equal(frame.src,"/cadastro-osc");
  assert.match(document.getElementById("support-secure-forms-notice").textContent,/login e envio de evidências/);
  assert.equal(calls.some(call=>call.path.endsWith("/messages")),false);
});

test("ditado preenche campo editável e só envia após confirmação explícita", async () => {
  const id = "33333333-3333-4333-8333-333333333333";
  const calls = [];
  const fetchAutenticado = async (path, options = {}) => {
    calls.push({ path, options });
    if (path === "/support-assistant/intro") return response({ assistantName:"Sophie", conversationsEnabled:true, aiAvailable:true });
    if (path === "/support-assistant/conversations") return response([]);
    if (path === "/support-assistant/voice-preference-scope") return response({ userId:700002 });
    if (path === "/support-assistant/conversations/new") return response({
      id, state:"AI_ACTIVE", createdAt:baseTime, updatedAt:baseTime, version:0, messages:[message(1,"SYSTEM","Olá! Sou a Sophie.")]
    });
    if (path === `/support-assistant/conversations/${id}/messages`) return response({
      ...conversation(id, "texto ditado sintético"), messages:[message(1,"SYSTEM","Olá! Sou a Sophie."),
        message(2,"USER","texto ditado sintético"), message(3,"AI","Posso ajudar com isso.")]
    });
    throw new Error("Unexpected path in dictation UI test.");
  };
  const document = await mountAssistant(fetchAutenticado);
  await document.getElementById("support-assistant-launcher").dispatch("click");
  for (let i = 0; i < 20 && document.getElementById("support-voice-onboarding").hidden; i++)
    await new Promise(resolve => setImmediate(resolve));
  assert.equal(document.getElementById("support-voice-onboarding").hidden, false);
  await document.getElementById("support-voice-enable").dispatch("click");
  await document.getElementById("support-voice-dictate").dispatch("click", { detail:1 });
  assert.equal(document.getElementById("support-message").value, "texto ditado sintético");
  assert.equal(calls.some(call => call.path.endsWith("/messages")), false,
    `Recognized speech remains editable and must not be submitted automatically. Requests: ${calls.map(call=>call.path).join(",")}`);
  await document.getElementById("support-voice-send").dispatch("click");
  for (let i = 0; i < 20 && !calls.some(call => call.path.endsWith("/messages")); i++)
    await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.filter(call => call.path.endsWith("/messages")).length, 1);
  assert.equal(JSON.parse(calls.find(call => call.path.endsWith("/messages")).options.body).body, "texto ditado sintético");
});

test("volunteer action preview requires an explicit click and shows success only after the server confirms", async () => {
  const id = "99999999-9999-4999-8999-999999999999";
  const draftId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const hash = "a".repeat(64);
  const calls = [];
  let completed = false;
  const draft = { id:draftId, opportunityId:71, title:"Mutirão de acolhimento", ngoName:"Casa Exemplo",
    details:"Capacidade publicada: 2. Local: Santos.", requestHash:hash, expiresAt:"2026-11-19T20:00:00Z", state:"DRAFT" };
  const fetchAutenticado = async (path, options = {}) => {
    calls.push({ path, options });
    if (path === "/support-assistant/intro") return response({ assistantName:"Sophie", conversationsEnabled:true, aiAvailable:true });
    if (path === "/support-assistant/conversations") return response([{ id, state:"AI_ACTIVE", unread:false }]);
    if (path === "/support-assistant/voice-preference-scope") return response({ userId:700077 });
    if (path === `/support-assistant/conversations/${id}`) return response({
      ...conversation(id,"Quero me candidatar ao mutirão"),
      messages:[message(1,"SYSTEM","Olá"), message(2,"USER","Quero me candidatar ao mutirão"),
        ...(completed ? [message(3,"AI","Sua candidatura foi enviada! Agora ela está aguardando a análise da ONG.")] : [])]
    });
    if (path === `/support-assistant/conversations/${id}/volunteer-application-drafts`) return response(completed ? [] : [draft]);
    if (path === `/support-assistant/conversations/${id}/volunteer-application-drafts/${draftId}/confirm`) {
      assert.equal(options.method,"POST");
      assert.deepEqual(JSON.parse(options.body),{ requestHash:hash });
      completed=true;
      return response({ ...draft, state:"COMPLETED", applicationId:902, applicationStatus:"PENDENTE",
        message:"Sua candidatura foi enviada! Agora ela está aguardando a análise da ONG." });
    }
    throw new Error(`Unexpected support API path: ${path}`);
  };
  const document = await mountAssistant(fetchAutenticado);
  await document.getElementById("support-assistant-launcher").dispatch("click");
  for (let i=0; i<30 && !document.getElementById("support-action-previews").children.length; i++)
    await new Promise(resolve => setImmediate(resolve));
  const preview = document.getElementById("support-action-previews").children[0];
  assert.ok(preview);
  assert.match(textContent(preview),/Mutirão de acolhimento/,calls.map(call=>call.path).join("\n"));
  assert.match(textContent(preview),/Casa Exemplo/);
  assert.equal(calls.some(call => call.path.endsWith("/confirm")),false,"rendering a preview must not execute it");
  const confirm = preview.children.flatMap(node => node.children || []).find(node => node.textContent === "Confirmar candidatura");
  assert.ok(confirm);
  await confirm.dispatch("click");
  assert.equal(calls.filter(call => call.path.endsWith("/confirm")).length,1);
  for (let i=0; i<30 && !document.getElementById("support-feedback").textContent; i++)
    await new Promise(resolve => setImmediate(resolve));
  assert.match(document.getElementById("support-feedback").textContent,/aguardando a análise da ONG/);
});
