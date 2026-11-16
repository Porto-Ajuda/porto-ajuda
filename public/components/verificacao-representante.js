import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado } from "./api.js";

(() => {
  const panel = document.getElementById("verificacao-representante-aplicacao");
  if (!panel || panel.dataset.initialized === "true") return;
  panel.dataset.initialized = "true";
  const privatePanel = document.getElementById("painel-dados");

  const status = document.getElementById("verificacao-representante-status");
  const operationalImpact = document.getElementById("verificacao-impacto-operacional");
  const feedback = document.getElementById("verificacao-representante-feedback");
  const evidenceList = document.getElementById("verificacao-representante-arquivos");
  const representationForm = document.getElementById("form-evidencia-representacao");
  const delegateForm = document.getElementById("form-evidencia-delegacao");
  const submitButton = document.getElementById("solicitar-revisao-verificacao");
  const enrollmentForm = document.getElementById("form-iniciar-verificacao");
  const reapplicationForm = document.getElementById("form-reaplicar-verificacao");
  const representationSelect = document.getElementById("tipo-representacao-verificacao");
  const enrollmentConfirmation = document.getElementById("confirmar-impacto-verificacao");
  const reapplicationSelect = document.getElementById("tipo-representacao-reaplicacao");
  const reapplicationConfirmation = document.getElementById("confirmar-reaplicacao-verificacao");
  const oscId = Number(new URLSearchParams(window.location.search).get("id"));
  const apiUrl = getAPI_URL();
  let application = null;
  let busy = false;
  let featureEnabled = false;
  let loading = false;

  if (!Number.isSafeInteger(oscId) || oscId < 1) return;

  const statusLabels = Object.freeze({
    PENDING: "Pendente de análise",
    NOT_STARTED: "Verificação ainda não iniciada",
    UNDER_REVIEW: "Em análise",
    INFORMATION_REQUESTED: "Informações adicionais solicitadas",
    APPROVED: "Representação aprovada",
    REJECTED: "Solicitação recusada"
  });
  const representationLabels = Object.freeze({
    LEGAL_REPRESENTATIVE: "Representante legal",
    AUTHORIZED_DELEGATE: "Pessoa delegada por representante legal"
  });

  function setFeedback(message, kind = "") {
    feedback.textContent = message;
    feedback.dataset.state = kind;
  }

  async function request(path, options = {}) {
    return fetchAutenticado(path, options);
  }

  async function reload() {
    if (!featureEnabled || !privatePanel || privatePanel.hidden || loading) {
      if (!privatePanel || privatePanel.hidden) clearPrivateState();
      return false;
    }
    loading = true;
    try {
      const response = await request(`/osc/${oscId}/verificacao`);
      if (!response.ok) {
        if (response.status === 401 || response.status === 403 || response.status === 404) {
          clearPrivateState();
          return false;
        }
        throw new Error("Não foi possível carregar a solicitação de verificação.");
      }
      if (!privatePanel || privatePanel.hidden) {
        clearPrivateState();
        return false;
      }
      application = await response.json();
      render();
      if (application.status === "NOT_STARTED" && application.reviewFeedback) {
        setFeedback(application.reviewFeedback);
      } else if (application.reviewFeedback) {
        setFeedback(`Retorno da análise: ${application.reviewFeedback}`);
      } else {
        setFeedback("");
      }
      return true;
    } finally {
      loading = false;
    }
  }

  function clearPrivateState() {
    panel.hidden = true;
    application = null;
    evidenceList.replaceChildren();
    setFeedback("");
  }

  function render() {
    if (!privatePanel || privatePanel.hidden) {
      clearPrivateState();
      return;
    }
    if (!application) return;
    panel.hidden = false;
    const statusText = statusLabels[application.status] || "Status de verificação indisponível";
    const roleText = representationLabels[application.representationType] || "Representação declarada";
    status.textContent = `${statusText} · ${roleText}`;
    if (operationalImpact) {
      operationalImpact.textContent = application.legacyExempt
        ? "Esta ONG já existia antes da verificação. A adesão voluntária e sua análise não mudam a visibilidade, o status operacional nem outras restrições."
        : "Esta ONG precisa de aprovação de representação para ficar pública e criar conteúdo comum. A aprovação não ativa a ONG nem remove arquivamento, bloqueios ou sanções.";
    }
    const canUpload = application.status === "INFORMATION_REQUESTED"
      || (application.status === "PENDING" && !application.submittedAt);
    const canSubmit = canUpload && Number(application.submissionVersion) > 0;
    enrollmentForm.hidden = application.status !== "NOT_STARTED" || application.canStartApplication !== true;
    enrollmentForm.querySelector("button").disabled = busy || enrollmentForm.hidden;
    representationSelect.disabled = busy || enrollmentForm.hidden;
    enrollmentConfirmation.disabled = busy || enrollmentForm.hidden;
    reapplicationForm.hidden = application.status !== "REJECTED" || application.canReapply !== true;
    reapplicationSelect.disabled = busy || reapplicationForm.hidden;
    reapplicationConfirmation.disabled = busy || reapplicationForm.hidden;
    reapplicationForm.querySelector("button").disabled = busy || reapplicationForm.hidden;
    if (application.status === "REJECTED" && application.canReapply !== true) {
      setFeedback("O limite de três solicitações por ONG foi atingido. O histórico anterior permanece disponível para análise administrativa.");
    }
    representationForm.hidden = !canUpload;
    delegateForm.hidden = !canUpload || application.representationType !== "AUTHORIZED_DELEGATE";
    submitButton.hidden = !canUpload;
    submitButton.disabled = busy || !canSubmit;
    representationForm.querySelector("button").disabled = busy || !canUpload;
    delegateForm.querySelector("button").disabled = busy || !canUpload;

    evidenceList.replaceChildren();
    for (const evidence of application.evidence || []) {
      const row = document.createElement("div");
      row.className = "verificacao-arquivo";
      const text = document.createElement("span");
      const kind = evidence.type === "DELEGATION_AUTHORIZATION" ? "Autorização do representante legal" : "Comprovação de representação";
      if (evidence.disposalStatus && evidence.disposalStatus !== "ACTIVE") {
        const disposalText = evidence.disposalStatus === "DISPOSAL_REQUESTED"
          ? "Descarte solicitado; o acesso está bloqueado."
          : "Documento removido conforme a política de retenção aprovada.";
        text.textContent = `${kind} · versão ${application.submissionVersion} · ${disposalText}`;
        row.append(text);
        evidenceList.append(row);
        continue;
      }
      const format = evidence.mediaType === "application/pdf" ? "PDF" : evidence.mediaType === "image/png" ? "PNG" : "JPEG";
      text.textContent = `${kind} · ${format} · versão ${application.submissionVersion}`;
      const link = document.createElement("a");
      link.href = "#";
      link.textContent = "Abrir documento (acesso temporário)";
      link.addEventListener("click", event => {
        event.preventDefault();
        void openEvidence(evidence.id);
      });
      row.append(text, link);
      evidenceList.append(row);
    }
  }

  async function startApplication(event) {
    event.preventDefault();
    if (busy || !enrollmentForm.reportValidity()) return;
    busy = true;
    setFeedback("Iniciando verificação…");
    render();
    try {
      const response = await request(`/osc/${oscId}/verificacao/solicitar`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          representationType: representationSelect.value,
          confirmPendingAndVisibilityEffect: enrollmentConfirmation.checked
        })
      });
      if (!response.ok) throw await responseError(response);
      application = await response.json();
      setFeedback("Solicitação iniciada. Envie os documentos obrigatórios para análise; o status e as restrições operacionais existentes foram preservados.", "success");
      render();
    } catch (error) {
      setFeedback(messageFor(error), "error");
    } finally {
      busy = false;
      render();
    }
  }

  async function reapplyAfterRejection(event) {
    event.preventDefault();
    if (busy || !reapplicationForm.reportValidity()) return;
    busy = true;
    setFeedback("Criando nova solicitação…");
    render();
    try {
      const response = await request(`/osc/${oscId}/verificacao/reaplicar`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          representationType: reapplicationSelect.value,
          confirmNewApplication: reapplicationConfirmation.checked
        })
      });
      if (!response.ok) throw await responseError(response);
      application = await response.json();
      reapplicationConfirmation.checked = false;
      setFeedback("Nova solicitação criada. A decisão e os documentos anteriores foram preservados. Envie a documentação corrigida.", "success");
      render();
    } catch (error) {
      setFeedback(messageFor(error), "error");
    } finally {
      busy = false;
      render();
    }
  }

  async function upload(form, type) {
    if (busy) return;
    const input = form.querySelector('input[type="file"]');
    const file = input.files?.[0];
    if (!file) {
      input.setCustomValidity("Selecione um documento.");
      input.reportValidity();
      return;
    }
    input.setCustomValidity("");
    if (file.size > 10 * 1024 * 1024) {
      input.setCustomValidity("O documento não pode ultrapassar 10 MB.");
      input.reportValidity();
      return;
    }
    busy = true;
    setFeedback("Enviando documento…");
    render();
    try {
      const body = new FormData();
      body.append("arquivo", file, file.name);
      const response = await request(`/osc/${oscId}/verificacao/documentos?tipo=${encodeURIComponent(type)}`, {
        method: "POST", body
      });
      if (!response.ok) throw await responseError(response);
      input.value = "";
      application = await response.json();
      setFeedback("Documento enviado para a versão atual.", "success");
      render();
    } catch (error) {
      setFeedback(messageFor(error), "error");
    } finally {
      busy = false;
      render();
    }
  }

  async function submitForReview() {
    if (busy || !application) return;
    busy = true;
    setFeedback("Enviando solicitação para análise…");
    render();
    try {
      const response = await request(`/osc/${oscId}/verificacao/enviar`, { method: "POST" });
      if (!response.ok) throw await responseError(response);
      application = await response.json();
      setFeedback("Solicitação enviada. A ONG continua sujeita ao seu status operacional e às demais restrições.", "success");
      render();
    } catch (error) {
      setFeedback(messageFor(error), "error");
    } finally {
      busy = false;
      render();
    }
  }

  async function openEvidence(evidenceId) {
    const tab = window.open("about:blank", "_blank");
    if (!tab) {
      setFeedback("Permita a abertura da nova aba para visualizar o documento temporariamente.", "error");
      return;
    }
    tab.opener = null;
    try {
      const response = await request(`/osc/${oscId}/verificacao/documentos/${encodeURIComponent(evidenceId)}/acesso`);
      if (!response.ok) throw await responseError(response);
      const access = await response.json();
      if (!access?.temporaryUrl || !access?.urlExpiresAt) throw new Error("Acesso temporário inválido.");
      tab.location.replace(access.temporaryUrl);
    } catch (error) {
      tab.close();
      setFeedback(messageFor(error), "error");
    }
  }

  async function responseError(response) {
    let message = "Não foi possível concluir esta etapa.";
    try {
      const body = await response.json();
      if (typeof body?.detail === "string") message = body.detail;
      else if (typeof body?.message === "string") message = body.message;
    } catch { /* resposta vazia */ }
    const error = new Error(message);
    error.status = response.status;
    return error;
  }

  function messageFor(error) {
    if (error?.status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
    if (error?.status === 403) return "Seu vínculo atual não autoriza esta solicitação.";
    if (error?.status === 409) return error.message || "A solicitação mudou. Atualize o perfil e tente novamente.";
    return error?.message || "Não foi possível concluir a etapa. Seus dados foram mantidos.";
  }

  representationForm.addEventListener("submit", event => {
    event.preventDefault();
    void upload(representationForm, "REPRESENTATION_PROOF");
  });
  enrollmentForm.addEventListener("submit", startApplication);
  reapplicationForm.addEventListener("submit", reapplyAfterRejection);
  delegateForm.addEventListener("submit", event => {
    event.preventDefault();
    void upload(delegateForm, "DELEGATION_AUTHORIZATION");
  });
  submitButton.addEventListener("click", () => void submitForReview());

  void (async () => {
    try {
      const response = await fetch(`${apiUrl}/osc/verificacao/config`, {
        credentials: "include", cache: "no-store", headers: { Accept: "application/json" }
      });
      if (!response.ok || (await response.json()).enabled !== true) return;
      featureEnabled = true;
      if (privatePanel && !privatePanel.hidden) await reload();
    } catch {
      // A falha do recurso opcional não interfere na navegação ou no perfil da ONG.
    }
  })();

  if (privatePanel && typeof MutationObserver === "function") {
    const observer = new MutationObserver(() => {
      if (privatePanel.hidden) clearPrivateState();
      else if (featureEnabled) void reload();
    });
    observer.observe(privatePanel, { attributes: true, attributeFilter: ["hidden"] });
  }
})();
