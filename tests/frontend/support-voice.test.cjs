const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

async function voiceModule() {
  const source = await readFile(resolve(__dirname, "../../public/components/support-voice.js"), "utf8");
  return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("ditado só começa após ação explícita e produz texto editável sem envio", async () => {
  const module = await voiceModule();
  const observed = { transcript: "", status: "", active: false };
  let instance;
  class Recognition {
    constructor() { instance = this; }
    start() { this.onstart?.(); }
    stop() { this.onend?.(); }
  }
  const controller = module.createSupportVoiceController({
    window: { SpeechRecognition: Recognition },
    onTranscript: text => { observed.transcript = text; },
    onStatus: text => { observed.status = text; },
    onDictation: active => { observed.active = active; }
  });
  assert.equal(instance, undefined, "Creating the control must not request microphone permission.");
  assert.equal(controller.startDictation(), true);
  assert.equal(instance.lang, "pt-BR");
  assert.equal(observed.active, true);
  instance.onresult({ results: [[{ transcript: "Encontre ONGs perto de mim" }]] });
  assert.equal(observed.transcript, "Encontre ONGs perto de mim");
  assert.match(observed.status, /revisão/);
  assert.equal(observed.active, true, "A transcript is only prepared; it is not sent by this controller.");
  controller.stopDictation();
  assert.equal(observed.active, false);
});

test("falhas de permissão e ausência de suporte mantêm o fallback por texto", async () => {
  const module = await voiceModule();
  let status = "";
  class DeniedRecognition { start() { this.onerror?.({ error: "not-allowed" }); } stop() {} }
  const denied = module.createSupportVoiceController({
    window: { webkitSpeechRecognition: DeniedRecognition }, onStatus: text => { status = text; }
  });
  denied.startDictation();
  assert.match(status, /permissão de microfone foi negada/);
  const unsupported = module.createSupportVoiceController({ window: {}, onStatus: text => { status = text; } });
  assert.equal(unsupported.startDictation(), false);
  assert.match(status, /digitar normalmente/);
});

test("leitura em voz alta é explícita, interrompe ditado e pode ser cancelada", async () => {
  const module = await voiceModule();
  let instance;
  let spoken = null;
  let cancelCount = 0;
  class Recognition { constructor() { instance = this; } start() {} stop() { this.onend?.(); } }
  class Utterance { constructor(text) { this.text = text; } }
  const synthesis = { getVoices: () => [], speak: utterance => { spoken = utterance; }, cancel: () => { cancelCount++; } };
  const controller = module.createSupportVoiceController({ window: {
    SpeechRecognition: Recognition, speechSynthesis: synthesis, SpeechSynthesisUtterance: Utterance
  } });
  assert.equal(spoken, null, "No speech should play on initialization.");
  controller.startDictation();
  assert.equal(controller.speak("Resposta sintética", { rate: 1.1 }), true);
  assert.equal(controller.dictating, false, "Speaking stops microphone capture before playback.");
  assert.equal(spoken.text, "Resposta sintética");
  assert.equal(spoken.lang, "pt-BR");
  assert.equal(spoken.rate, 1.1);
  controller.stopSpeaking();
  assert.ok(cancelCount >= 1);
});

test("preferências normalizam limites e toleram armazenamento indisponível", async () => {
  const module = await voiceModule();
  const preference = module.normalizeVoicePreference({ enabled: true, rate: 9, voiceURI: "x".repeat(200) });
  assert.equal(preference.enabled, true);
  assert.equal(preference.rate, 1.5);
  assert.equal(preference.avoidScreenReaderAnnouncements, true);
  assert.equal(preference.voiceURI.length, 160);
  assert.equal(module.writeVoicePreference(null, "key", preference), true);
  assert.equal(module.readVoicePreference(null, "key"), null);
});
