const DEFAULTS = Object.freeze({
  enabled: false,
  readAloud: false,
  avoidScreenReaderAnnouncements: true,
  rate: 1,
  voiceURI: ""
});

export function normalizeVoicePreference(value = {}) {
  const rate = Number(value.rate);
  return {
    enabled: value.enabled === true,
    readAloud: value.readAloud === true,
    avoidScreenReaderAnnouncements: value.avoidScreenReaderAnnouncements !== false,
    rate: Number.isFinite(rate) ? Math.min(1.5, Math.max(0.75, rate)) : 1,
    voiceURI: typeof value.voiceURI === "string" ? value.voiceURI.slice(0, 160) : ""
  };
}

export function readVoicePreference(storage, key) {
  try {
    const value = storage?.getItem(key);
    return value ? normalizeVoicePreference(JSON.parse(value)) : null;
  } catch { return null; }
}

export function writeVoicePreference(storage, key, preference) {
  try { storage?.setItem(key, JSON.stringify(normalizeVoicePreference(preference))); return true; }
  catch { return false; }
}

export function createSupportVoiceController({ window, onTranscript, onStatus, onSpeaking, onDictation }) {
  let recognition = null;
  let speaking = false;
  let utterance = null;
  const Recognition = window?.SpeechRecognition || window?.webkitSpeechRecognition;
  const supported = typeof Recognition === "function";
  const synthesis = window?.speechSynthesis;

  function status(message) { onStatus?.(message); }
  function stopDictation() {
    if (!recognition) return;
    try { recognition.stop(); } catch { /* recognition might already have ended */ }
    onDictation?.(false);
  }
  function startDictation() {
    if (!supported) { status("Seu navegador não oferece ditado por voz aqui. Você ainda pode digitar normalmente."); return false; }
    if (recognition) return true;
    try {
      recognition = new Recognition();
      recognition.lang = "pt-BR";
      recognition.interimResults = false;
      recognition.continuous = false;
      recognition.onstart = () => { onDictation?.(true); status("Ditado ativo. Fale quando estiver pronto; a transcrição não será enviada automaticamente."); };
      recognition.onresult = event => {
        const transcript = Array.from(event.results || []).map(result => result?.[0]?.transcript || "").join(" ").trim();
        if (transcript) onTranscript?.(transcript);
        status(transcript ? "Transcrição pronta para revisão. Edite, envie ou cancele." : "Não houve transcrição. Você pode tentar novamente ou digitar.");
      };
      recognition.onerror = event => {
        onDictation?.(false);
        const messages = {
          "not-allowed": "A permissão de microfone foi negada. Digite sua mensagem ou altere a permissão do navegador.",
          "service-not-allowed": "O serviço de reconhecimento de voz não está disponível. Digite sua mensagem.",
          "no-speech": "Não detectei fala. Você pode tentar novamente ou digitar.",
          "audio-capture": "Não foi possível acessar um microfone. Digite sua mensagem.",
          network: "O reconhecimento de voz falhou por conexão. Tente novamente ou digite.",
          aborted: "O ditado foi cancelado."
        };
        status(messages[event?.error] || "O ditado foi interrompido. Você pode tentar novamente ou digitar.");
      };
      recognition.onend = () => { recognition = null; onDictation?.(false); };
      recognition.start();
      return true;
    } catch {
      recognition = null;
      status("Não foi possível iniciar o ditado. Verifique a permissão do navegador ou digite sua mensagem.");
      return false;
    }
  }
  function speak(text, options = {}) {
    if (!synthesis || typeof window?.SpeechSynthesisUtterance !== "function") {
      status("A leitura em voz alta não está disponível neste navegador. O texto continua acessível na conversa.");
      return false;
    }
    stopDictation();
    stopSpeaking();
    try {
      utterance = new window.SpeechSynthesisUtterance(String(text || "").slice(0, 2400));
      utterance.lang = "pt-BR";
      const preference = normalizeVoicePreference(options);
      utterance.rate = preference.rate;
      const voice = (synthesis.getVoices?.() || []).find(item => item.voiceURI === preference.voiceURI);
      if (voice) utterance.voice = voice;
      utterance.onstart = () => { speaking = true; onSpeaking?.(true); };
      utterance.onend = utterance.onerror = () => { speaking = false; utterance = null; onSpeaking?.(false); };
      synthesis.speak(utterance);
      return true;
    } catch {
      utterance = null; speaking = false; onSpeaking?.(false);
      status("Não foi possível iniciar a leitura em voz alta. O texto continua disponível.");
      return false;
    }
  }
  function stopSpeaking() {
    if (synthesis && (speaking || utterance)) synthesis.cancel();
    speaking = false; utterance = null; onSpeaking?.(false);
  }
  return { supported, synthesisSupported: Boolean(synthesis), startDictation, stopDictation, speak, stopSpeaking,
    voices() { return (synthesis?.getVoices?.() || []).filter(voice => String(voice.lang || "").toLowerCase().startsWith("pt")); },
    get dictating() { return recognition !== null; }, get speaking() { return speaking; } };
}

export const DEFAULT_VOICE_PREFERENCE = DEFAULTS;
