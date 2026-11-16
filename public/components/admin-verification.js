import { showAdminDialog } from "./admin-rbac.js";

export function initializeAdminVerification({ apiUrl, onUnauthorized = () => {}, fetchImpl = fetch }) {
  const elements = {
    status: document.getElementById("status-verificacao-representantes"),
    list: document.getElementById("lista-verificacao-representantes"),
    filter: document.getElementById("filtro-verificacao-representantes"),
    refresh: document.getElementById("atualizar-verificacoes-representantes"),
    previous: document.getElementById("verificacoes-representantes-anterior"),
    next: document.getElementById("verificacoes-representantes-proxima"),
    page: document.getElementById("verificacoes-representantes-pagina"),
    dialog: document.getElementById("dialogo-verificacao-representante"),
    description: document.getElementById("descricao-dialogo-verificacao-representante"),
    documents: document.getElementById("documentos-verificacao-representante"),
    aiAnalysis: document.getElementById("analise-ia-verificacao"),
    aiAnalysisContent: document.getElementById("conteudo-analise-ia-verificacao"),
    start: document.getElementById("iniciar-revisao-verificacao"),
    form: document.getElementById("formulario-revisao-verificacao"),
    decision: document.getElementById("decisao-verificacao-representante"),
    decisionField: document.getElementById("campo-decisao-verificacao"),
    reason: document.getElementById("justificativa-verificacao-representante"),
    reasonLabel: document.getElementById("rotulo-justificativa-verificacao"),
    feedback: document.getElementById("feedback-verificacao-representante"),
    cancel: document.getElementById("cancelar-revisao-verificacao"),
    confirm: document.getElementById("confirmar-revisao-verificacao"),
    reopen: document.getElementById("reabrir-verificacao")
  };
  let currentPage = 0;
  let totalElements = 0;
  let activeApplicationId = null;
  let busy = false;
  let detail = null;

  const labels = Object.freeze({
    PENDING: "Pendente",
    UNDER_REVIEW: "Em análise",
    INFORMATION_REQUESTED: "Informações adicionais solicitadas",
    APPROVED: "Aprovada",
    REJECTED: "Recusada",
    LEGAL_REPRESENTATIVE: "Representante legal",
    AUTHORIZED_DELEGATE: "Pessoa delegada por representante legal"
  });
  const aiLabels = Object.freeze({
    QUEUED: "Na fila", PROCESSING: "Em processamento", RETRY: "Aguardando nova tentativa",
    COMPLETED: "Análise concluída", FAILED: "Análise não concluída", SUPERSEDED: "Versão substituída",
    EVIDENCIA_COMPATIVEL: "Evidências compatíveis", DIVERGENCIA: "Divergência identificada",
    INCONCLUSIVO: "Resultado inconclusivo",
    NOME_ENTIDADE: "Nome da entidade", CNPJ: "CNPJ", REPRESENTANTE: "Representante",
    REPRESENTANTE_LEGAL: "Representante legal indicado nos documentos",
    DELEGADO_AUTORIZADO: "Pessoa delegada indicada nos documentos",
    REPRESENTANTE_AUTORIZANTE: "Representante que autoriza a delegação",
    PODERES_REPRESENTACAO: "Poderes de representação", ESCOPO_DELEGACAO: "Escopo da delegação",
    VALIDADE: "Validade", ASSINATURA: "Assinatura",
    ENCONTRADO: "Encontrado", AUSENTE: "Ausente", INCERTO: "Incerto", CONFLITANTE: "Conflitante",
    NOME_ENTIDADE_DIVERGENTE: "Nome da entidade divergente", CNPJ_DIVERGENTE: "CNPJ divergente",
    REPRESENTANTE_NAO_IDENTIFICADO: "Representante não identificado",
    REPRESENTANTE_LEGAL_NAO_IDENTIFICADO: "Representante legal não identificado",
    DELEGADO_AUTORIZADO_NAO_IDENTIFICADO: "Pessoa delegada não identificada",
    REPRESENTANTE_AUTORIZANTE_NAO_IDENTIFICADO: "Representante autorizante não identificado",
    PODERES_NAO_COMPROVADOS: "Poderes não comprovados", DELEGACAO_SEM_ESCOPO: "Delegação sem escopo comprovado",
    DELEGACAO_VENCIDA: "Delegação vencida", ASSINATURA_INCONCLUSIVA: "Assinatura inconclusiva",
    DOCUMENTOS_CONFLITANTES: "Documentos conflitantes", OUTRA: "Outra divergência",
    BAIXA: "Baixa", MEDIA: "Média", ALTA: "Alta",
    PROVIDER_QUOTA_EXHAUSTED: "Cota temporariamente indisponível",
    PROVIDER_REFUSAL: "Provedor recusou a análise", INCOMPLETE_RESPONSE: "Resposta incompleta",
    INVALID_DOCUMENT: "Documento inválido ou ilegível", INVALID_RESULT: "Resultado inválido",
    PROVIDER_CONFIGURATION_ERROR: "Configuração do provedor indisponível",
    PROVIDER_UNAVAILABLE: "Provedor indisponível", PROVIDER_TIMEOUT: "Tempo de análise esgotado",
    PROVIDER_REJECTED: "A análise não pôde ser concluída", EVIDENCE_UNAVAILABLE: "Evidência indisponível",
    EVIDENCE_LIMIT_EXCEEDED: "Limite de evidências excedido", WORKER_LEASE_EXPIRED: "Processamento expirado",
    INTERNAL_RETRYABLE: "Falha temporária no processamento",
    MATCHES_REGISTERED_DATA: "Compatível com nome civil/social cadastrado (não verificado)",
    MISMATCH: "Nome difere do cadastro; requer revisão humana e não indica fraude",
    INSUFFICIENT_EVIDENCE: "Evidência insuficiente para comparar a identidade",
    INDEPENDENTLY_VERIFIED: "Verificado por fonte independente",
    NOT_APPLICABLE: "Não se aplica", CONSISTENT_DOCUMENTARY_CHAIN: "Cadeia documental consistente (não verificada independentemente)",
    INCOMPLETE: "Cadeia documental incompleta", CONFLICTING: "Cadeia documental conflitante",
    APPARENTLY_CURRENT: "Data documental aparentemente vigente (não verificada)",
    EXPIRED: "Data de validade expirada", UNVERIFIABLE: "Validade não verificável"
  });

  async function getJson(path) {
    const response = await fetchImpl(`${apiUrl}${path}`, {
      credentials: "include", cache: "no-store", headers: { Accept: "application/json" }
    });
    if (response.status === 401) { onUnauthorized(); throw new Error("Sessão administrativa expirada."); }
    if (!response.ok) throw new Error(response.status === 403
      ? "Seu perfil não pode revisar verificações de representação."
      : "Não foi possível carregar as solicitações de verificação.");
    return response.json();
  }

  async function postJson(path, payload = undefined) {
    const csrfResponse = await fetchImpl(`${apiUrl}/usuario/csrf`, {
      credentials: "include", cache: "no-store", headers: { Accept: "application/json" }
    });
    if (csrfResponse.status === 401) { onUnauthorized(); throw new Error("Sessão administrativa expirada."); }
    if (!csrfResponse.ok) throw new Error("Não foi possível validar a proteção CSRF.");
    const csrf = await csrfResponse.json();
    if (!csrf?.headerName || !csrf?.token) throw new Error("Token CSRF indisponível.");
    const headers = { Accept: "application/json", [csrf.headerName]: csrf.token };
    const options = { method: "POST", credentials: "include", cache: "no-store", headers };
    if (payload !== undefined) {
      headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(payload);
    }
    const response = await fetchImpl(`${apiUrl}${path}`, options);
    if (response.status === 401) { onUnauthorized(); throw new Error("Sessão administrativa expirada."); }
    if (!response.ok) {
      throw new Error(response.status === 403 ? "Ação não autorizada para seu perfil."
        : response.status === 404 ? "Solicitação não encontrada."
          : response.status === 409 ? "A solicitação mudou. Atualize a fila antes de tentar novamente."
            : response.status === 400 ? "A decisão ou justificativa foi recusada. Revise os campos."
              : "Não foi possível confirmar a operação. Atualize a fila antes de repetir.");
    }
    return response.json();
  }

  function pageCount() { return Math.max(1, Math.ceil(totalElements / 20)); }

  function updatePagination() {
    const pages = pageCount();
    elements.page.textContent = `Página ${currentPage + 1} de ${pages} · ${totalElements} solicitações`;
    elements.previous.disabled = busy || currentPage === 0;
    elements.next.disabled = busy || currentPage + 1 >= pages;
  }

  async function load(page = 0) {
    if (busy) return false;
    busy = true;
    currentPage = Math.max(0, page);
    elements.list.setAttribute("aria-busy", "true");
    elements.status.textContent = "Carregando solicitações…";
    elements.list.replaceChildren();
    updatePagination();
    try {
      const query = new URLSearchParams({ page: String(currentPage), size: "20" });
      if (elements.filter.value) query.set("status", elements.filter.value);
      const result = await getJson(`/admin/ngo-verifications?${query}`);
      totalElements = Number.isSafeInteger(result.totalElements) ? result.totalElements : 0;
      for (const item of result.items || []) elements.list.append(renderQueueItem(item));
      elements.status.textContent = totalElements ? "Solicitações carregadas." : "Não há solicitações neste filtro.";
      updatePagination();
      return true;
    } catch (error) {
      elements.status.textContent = error.message || "Falha ao carregar as solicitações.";
      updatePagination();
      return false;
    } finally {
      busy = false;
      elements.list.setAttribute("aria-busy", "false");
      updatePagination();
    }
  }

  function renderQueueItem(item) {
    const card = document.createElement("article");
    card.className = "admin-verification-card";
    const title = document.createElement("h2");
    title.textContent = item.organizationName || `ONG ${item.oscId}`;
    const metadata = document.createElement("p");
    metadata.textContent = `${labels[item.status] || "Status indisponível"} · ${labels[item.representationType] || "Representação declarada"}`;
    const date = document.createElement("time");
    date.dateTime = item.submittedAt || "";
    date.textContent = item.submittedAt ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(item.submittedAt)) : "Envio sem data";
    const open = document.createElement("button");
    open.type = "button";
    open.className = "admin-primary-button";
    open.textContent = "Analisar solicitação";
    open.addEventListener("click", () => void openDetail(item.applicationId));
    card.append(title, metadata, date, open);
    return card;
  }

  async function openDetail(applicationId) {
    if (busy || !Number.isSafeInteger(Number(applicationId))) return;
    busy = true;
    activeApplicationId = Number(applicationId);
    detail = null;
    elements.documents.replaceChildren();
    elements.aiAnalysisContent.replaceChildren();
    elements.aiAnalysis.hidden = true;
    elements.description.textContent = "Carregando dados e documentos privados…";
    elements.feedback.textContent = "";
    elements.start.hidden = true;
    elements.start.disabled = true;
    elements.decision.disabled = true;
    elements.reason.disabled = true;
    elements.confirm.hidden = true;
    elements.confirm.disabled = true;
    elements.reopen.hidden = true;
    elements.reopen.disabled = true;
    showAdminDialog(elements.dialog);
    try {
      detail = await getJson(`/admin/ngo-verifications/${encodeURIComponent(activeApplicationId)}`);
      renderDetail();
    } catch (error) {
      elements.description.textContent = error.message || "Não foi possível abrir a solicitação.";
    } finally {
      busy = false;
      updateDialogControls();
    }
  }

  function renderDetail() {
    const status = labels[detail.status] || "Status indisponível";
    const representation = labels[detail.representationType] || "Representação declarada";
    elements.description.textContent = `${detail.organizationName || `ONG ${detail.oscId}`} · ${representation} · ${status} · versão documental ${detail.submissionVersion}`;
    elements.documents.replaceChildren();
    if (!(detail.evidence || []).length) {
      const empty = document.createElement("p");
      empty.textContent = "Esta versão ainda não contém documentos.";
      elements.documents.append(empty);
    }
    for (const evidence of detail.evidence || []) {
      const item = document.createElement("div");
      item.className = "admin-verification-document";
      const label = document.createElement("span");
      const documentType = evidence.type === "DELEGATION_AUTHORIZATION" ? "Autorização do representante legal" : "Comprovação de representação";
      const submitted = evidence.submissionAt
        ? ` · versão ${evidence.submissionVersion} enviada em ${new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(evidence.submissionAt))}`
        : ` · versão ${evidence.submissionVersion}`;
      label.textContent = documentType + submitted;
      if (evidence.disposalStatus && evidence.disposalStatus !== "ACTIVE") {
        label.textContent += evidence.disposalStatus === "DISPOSAL_REQUESTED"
          ? " · descarte solicitado; acesso bloqueado"
          : " · documento removido conforme política aprovada";
        item.append(label);
        elements.documents.append(item);
        continue;
      }
      const open = document.createElement("a");
      let safeUrl = null;
      try {
        const candidate = new URL(evidence.temporaryUrl);
        if (candidate.protocol === "https:" || candidate.origin === window.location.origin) safeUrl = candidate.href;
      } catch { safeUrl = null; }
      if (safeUrl && evidence.urlExpiresAt) {
        open.href = safeUrl;
        open.target = "_blank";
        open.rel = "noopener noreferrer";
        open.textContent = "Abrir documento (link temporário)";
      } else {
        open.textContent = "Acesso ao documento indisponível";
        open.setAttribute("aria-disabled", "true");
      }
      item.append(label, open);
      elements.documents.append(item);
    }
    renderAiAnalysis(detail.aiAnalysis);
    elements.start.hidden = detail.status !== "PENDING";
    elements.decisionField.hidden = detail.status !== "UNDER_REVIEW";
    elements.reasonLabel.textContent = detail.status === "REJECTED"
      ? "Justificativa obrigatória para reabertura" : "Justificativa obrigatória";
    elements.decision.disabled = detail.status !== "UNDER_REVIEW";
    elements.reason.disabled = detail.status !== "UNDER_REVIEW";
    elements.confirm.hidden = detail.status !== "UNDER_REVIEW";
    elements.confirm.disabled = detail.status !== "UNDER_REVIEW";
    elements.reopen.hidden = detail.status !== "REJECTED";
    elements.reopen.disabled = detail.status !== "REJECTED";
    elements.reason.disabled = !["UNDER_REVIEW", "REJECTED"].includes(detail.status);
    if (detail.status !== "UNDER_REVIEW") {
      elements.decision.value = "";
      elements.reason.value = "";
    }
  }

  function renderAiAnalysis(analysis) {
    elements.aiAnalysisContent.replaceChildren();
    elements.aiAnalysis.hidden = false;
    if (!analysis) {
      const empty = document.createElement("p");
      empty.textContent = detail.aiAnalysisResultsReadable === false
        ? "A leitura de análises armazenadas está desativada nesta instância. Nenhum documento será enviado para análise."
        : detail.aiAnalysisProcessingEnabled === false
          ? "A análise automática está desativada e não há resultado armazenado para esta versão. A revisão humana segue disponível."
          : "Nenhum trabalho de análise está registrado para esta versão. A revisão humana segue disponível.";
      elements.aiAnalysisContent.append(empty);
      return;
    }
    const status = document.createElement("p");
    status.className = "admin-verification-ai-summary__status";
    const version = document.createElement("p");
    version.className = "admin-verification-ai-summary__note";
    version.textContent = `Resultado associado à versão enviada ${detail.submissionVersion}. A análise é apenas consultiva.`;
    elements.aiAnalysisContent.append(version);
    const metadata = [aiLabels[analysis.status] || "Status indisponível"];
    if (analysis.model) metadata.push(analysis.model);
    if (Number.isFinite(analysis.inputTokens) && Number.isFinite(analysis.outputTokens)) {
      metadata.push(`${analysis.inputTokens} tokens de entrada · ${analysis.outputTokens} de saída`);
    }
    status.textContent = metadata.join(" · ");
    elements.aiAnalysisContent.append(status);

    if (analysis.status === "COMPLETED") {
      const conclusion = document.createElement("p");
      conclusion.textContent = `Conclusão indicativa: ${aiLabels[analysis.conclusion] || "Inconclusiva"}.`;
      elements.aiAnalysisContent.append(conclusion);
      const identity = document.createElement("p");
      identity.className = "admin-verification-ai-summary__note";
      identity.textContent = analysis.identityComparison
        ? `Comparação documental de identidade: ${aiLabels[analysis.identityComparison] || "Resultado indisponível"}.`
        : "Esta versão da análise não contém comparação documental com os nomes cadastrados.";
      elements.aiAnalysisContent.append(identity);
      if (analysis.delegationEvidenceChain && analysis.delegationEvidenceChain !== "NOT_APPLICABLE") {
        const chain = document.createElement("p");
        chain.textContent = `Delegação: ${aiLabels[analysis.delegationEvidenceChain] || "Resultado indisponível"}.`;
        elements.aiAnalysisContent.append(chain);
      }
      if (analysis.apparentValidity && analysis.apparentValidity !== "NOT_APPLICABLE") {
        const validity = document.createElement("p");
        const expiry = analysis.expiryDate ? ` · expiração indicada: ${analysis.expiryDate}` : "";
        validity.textContent = `Validade documental: ${aiLabels[analysis.apparentValidity] || "Não verificável"}${expiry}.`;
        elements.aiAnalysisContent.append(validity);
      }
      const fields = document.createElement("ul");
      for (const field of analysis.fields || []) {
        const item = document.createElement("li");
        const pages = (field.references || []).map(reference =>
          `evidência ${reference.evidenceId}, página ${reference.page}`).join("; ");
        item.textContent = `${aiLabels[field.field] || "Campo"}: ${aiLabels[field.status] || "Indisponível"}`
          + (pages ? ` — ${pages}` : "");
        fields.append(item);
      }
      if (fields.childElementCount) elements.aiAnalysisContent.append(fields);
      const discrepancies = document.createElement("ul");
      discrepancies.className = "admin-verification-ai-summary__discrepancies";
      for (const discrepancy of analysis.discrepancies || []) {
        const item = document.createElement("li");
        const pages = (discrepancy.references || []).map(reference =>
          `evidência ${reference.evidenceId}, página ${reference.page}`).join("; ");
        item.textContent = `${aiLabels[discrepancy.code] || "Divergência"} · severidade ${aiLabels[discrepancy.severity] || "indisponível"}`
          + (pages ? ` — ${pages}` : "");
        discrepancies.append(item);
      }
      if (discrepancies.childElementCount) elements.aiAnalysisContent.append(discrepancies);
      const privacyNote = document.createElement("p");
      privacyNote.className = "admin-verification-ai-summary__note";
      privacyNote.textContent = "Valores extraídos não são exibidos. Referências de página e achados devem ser conferidos no documento original.";
      elements.aiAnalysisContent.append(privacyNote);
    } else if (analysis.errorCode) {
      const failure = document.createElement("p");
      failure.textContent = `${aiLabels[analysis.errorCode] || "Análise indisponível"}. Faça a revisão manual dos documentos.`;
      elements.aiAnalysisContent.append(failure);
    } else {
      const pending = document.createElement("p");
      pending.textContent = "A análise é auxiliar e não toma decisão. Faça a revisão humana conforme necessário.";
      elements.aiAnalysisContent.append(pending);
    }
  }

  function updateDialogControls() {
    elements.start.disabled = busy;
    elements.cancel.disabled = busy;
    elements.confirm.disabled = busy || detail?.status !== "UNDER_REVIEW";
    elements.reopen.disabled = busy || detail?.status !== "REJECTED";
    elements.decision.disabled = busy || detail?.status !== "UNDER_REVIEW";
    elements.reason.disabled = busy || !["UNDER_REVIEW", "REJECTED"].includes(detail?.status);
  }

  async function startReview() {
    if (busy || !activeApplicationId || detail?.status !== "PENDING") return;
    busy = true;
    elements.feedback.textContent = "Iniciando análise…";
    updateDialogControls();
    try {
      detail = await postJson(`/admin/ngo-verifications/${activeApplicationId}/iniciar`);
      renderDetail();
      elements.feedback.textContent = "Revisão humana iniciada. A análise assistida existente foi preservada; nenhuma nova chamada de IA foi feita.";
    } catch (error) {
      elements.feedback.textContent = error.message;
    } finally {
      busy = false;
      updateDialogControls();
    }
  }

  async function decide(event) {
    event.preventDefault();
    if (busy || !activeApplicationId || detail?.status !== "UNDER_REVIEW") return;
    if (!elements.form.reportValidity()) return;
    const payload = { decision: elements.decision.value, justification: elements.reason.value.trim() };
    if (!payload.justification) {
      elements.reason.setCustomValidity("A justificativa é obrigatória.");
      elements.reason.reportValidity();
      return;
    }
    elements.reason.setCustomValidity("");
    busy = true;
    elements.feedback.textContent = "Registrando decisão…";
    updateDialogControls();
    let reloadQueue = false;
    try {
      detail = await postJson(`/admin/ngo-verifications/${activeApplicationId}/decisao`, payload);
      elements.dialog.close();
      reloadQueue = true;
    } catch (error) {
      elements.feedback.textContent = error.message;
    } finally {
      busy = false;
      updateDialogControls();
    }
    if (reloadQueue) await load(currentPage);
  }

  async function reopenRejected() {
    if (busy || !activeApplicationId || detail?.status !== "REJECTED") return;
    const justification = elements.reason.value.trim();
    if (!justification) {
      elements.reason.setCustomValidity("A justificativa é obrigatória para reabrir a solicitação.");
      elements.reason.reportValidity();
      return;
    }
    if (justification.length > 2000) return;
    elements.reason.setCustomValidity("");
    busy = true;
    elements.feedback.textContent = "Reabrindo solicitação…";
    updateDialogControls();
    let reloadQueue = false;
    try {
      detail = await postJson(`/admin/ngo-verifications/${activeApplicationId}/reabrir`, { justification });
      elements.dialog.close();
      reloadQueue = true;
    } catch (error) {
      elements.feedback.textContent = error.message;
    } finally {
      busy = false;
      updateDialogControls();
    }
    if (reloadQueue) await load(currentPage);
  }

  elements.refresh.addEventListener("click", () => void load(currentPage));
  elements.filter.addEventListener("change", () => void load(0));
  elements.previous.addEventListener("click", () => void load(currentPage - 1));
  elements.next.addEventListener("click", () => void load(currentPage + 1));
  elements.start.addEventListener("click", () => void startReview());
  elements.form.addEventListener("submit", decide);
  elements.reopen.addEventListener("click", () => void reopenRejected());
  elements.cancel.addEventListener("click", () => elements.dialog.close());

  return {
    load,
    clear() {
      elements.list.replaceChildren();
      elements.status.textContent = "";
      elements.dialog.close();
      detail = null;
      activeApplicationId = null;
      totalElements = 0;
      currentPage = 0;
    },
    isMutating() { return busy; }
  };
}
