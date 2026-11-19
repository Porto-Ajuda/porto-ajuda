import { fetchAutenticado } from "./api.js";
import { campaignCurrencyToCents, campaignMoney } from "./campaign-money.js";

const oscId = Number(new URLSearchParams(location.search).get("id"));
const form = document.getElementById("form-campanha-doacao");

if (form) {
  const dialog = document.getElementById("dialogo-campanha-doacao");
  const list = document.getElementById("campanhas-gestao-lista");
  const status = document.getElementById("campanhas-gestao-status");
  const empty = document.getElementById("campanhas-gestao-vazio");
  const error = document.getElementById("campanhas-gestao-erro");
  const feedback = document.getElementById("campanha-form-feedback");
  const image = document.getElementById("campanha-imagem");
  const preview = document.getElementById("campanha-imagem-preview");
  const previewImage = preview.querySelector("img");
  const previewName = preview.querySelector("figcaption span");
  const removeImage = preview.querySelector("figcaption button");
  const meta = document.getElementById("campanha-meta");
  const metaError = document.getElementById("campanha-meta-erro");
  const publishControl = document.getElementById("campanha-publicar-controle");
  const publish = document.getElementById("campanha-publicar");
  const editPath = id => `/donation-campaigns/${id}/organizations/${oscId}`;
  let current = null;
  let currentImage = null;
  let previewUrl = null;
  let items = [];

  const money = campaignMoney;

  function safeImageUrl(value) {
    if (typeof value !== "string" || !value.trim()) return null;
    try {
      const url = new URL(value, location.href);
      return ["https:", "http:"].includes(url.protocol) ? url.href : null;
    } catch { return null; }
  }

  async function json(response) {
    if (response?.ok) return response.status === 204 ? null : response.json();
    const statusCode = response?.status;
    if (statusCode === 400 || statusCode === 422) {
      try { const body = await response.json(); if (body.detail && !String(body.detail).includes("PreparedStatement")) throw new Error(body.detail); }
      catch (cause) { if (cause instanceof Error && cause.message !== "Unexpected end of JSON input") throw cause; }
      throw new Error("Revise os campos informados e tente novamente.");
    }
    if (statusCode === 401) throw new Error("Sua sessão expirou. Entre novamente para continuar.");
    if (statusCode === 403) throw new Error("Somente criadores e administradores autorizados podem gerenciar esta vaquinha.");
    if (statusCode === 404) throw new Error("A ONG ou a vaquinha não está mais disponível.");
    if (statusCode === 409) throw new Error("A vaquinha mudou desde que foi carregada. Atualize a lista e tente novamente.");
    if (statusCode === 413) throw new Error("A imagem precisa ter até 10 MB.");
    throw new Error("Não foi possível salvar a vaquinha agora. Seus dados foram mantidos; tente novamente.");
  }

  function clearPreviewUrl() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
  }

  function renderPreview(file = null) {
    clearPreviewUrl();
    const source = file ? (previewUrl = URL.createObjectURL(file)) : safeImageUrl(currentImage);
    preview.hidden = !source;
    if (source) previewImage.src = source;
    else previewImage.removeAttribute("src");
    previewName.textContent = file?.name || (currentImage ? "Imagem atual da vaquinha." : "");
    removeImage.hidden = !file;
  }

  function render() {
    list.replaceChildren();
    empty.hidden = items.length !== 0;
    for (const item of items) {
      const card = document.createElement("article"); card.className = "campanha-gestao-card";
      const media = document.createElement("div"); media.className = "campanha-gestao-card__media";
      const imageUrl=safeImageUrl(item.imageUrl);
      if(imageUrl){const img=document.createElement("img");img.src=imageUrl;img.alt=`Imagem da vaquinha ${item.title}`;img.loading="lazy";media.append(img);}
      else {const fallback=document.createElement("span");fallback.textContent="Vaquinha da ONG";media.append(fallback);}
      const body=document.createElement("div");body.className="campanha-gestao-card__body";
      const title = document.createElement("h4"); title.textContent = item.title;
      const stateName = ({ DRAFT: "Rascunho", ACTIVE: "Ativa", PAUSED: "Pausada", ENDED: "Encerrada" })[item.state] || item.state;
      const state = document.createElement("span"); state.className=`campanha-gestao-card__badge${item.suspended?" campanha-gestao-card__badge--suspensa":""}`; state.textContent = item.suspended ? "Suspensa pela moderação" : stateName;
      const sums = document.createElement("p"); sums.className="campanha-gestao-card__sums"; sums.textContent = `${money(item.raisedNetCents)} arrecadados de ${money(item.goalNetCents)} líquidos`;
      const progress = document.createElement("progress"); progress.max = 100; progress.value = Math.min(100, Math.floor(100 * Number(item.raisedNetCents || 0) / Number(item.goalNetCents || 1))); progress.setAttribute("aria-label", `${progress.value}% da meta líquida`);
      const actions = document.createElement("div"); actions.className = "campanha-gestao-acoes";
      const edit = document.createElement("button"); edit.type = "button"; edit.className = "btn btn-secundario"; edit.textContent = "Editar"; edit.addEventListener("click", () => open(item));
      actions.append(edit);
      if (!item.suspended && item.state !== "ENDED") {
        const next = item.state === "ACTIVE" ? "PAUSED" : "ACTIVE";
        const toggle = document.createElement("button"); toggle.type = "button"; toggle.className = "btn btn-secundario";
        toggle.textContent = item.state === "ACTIVE" ? "Pausar" : item.state === "PAUSED" ? "Retomar" : "Publicar";
        toggle.addEventListener("click", () => changeState(item, next)); actions.append(toggle);
        const end = document.createElement("button"); end.type = "button"; end.className = "btn btn-secundario"; end.textContent = "Encerrar";
        end.addEventListener("click", () => changeState(item, "ENDED", true)); actions.append(end);
      }
      body.append(state,title,sums,progress,actions);card.append(media,body);list.append(card);
    }
  }

  async function load() {
    error.hidden = true; status.textContent = "Carregando vaquinhas…"; list.setAttribute("aria-busy","true");
    try {
      if (!Number.isSafeInteger(oscId) || oscId < 1) throw new Error("Não foi possível identificar a ONG desta página.");
      const response = await fetchAutenticado(`/donation-campaigns/organizations/${oscId}/manage`);
      items = await json(response) || []; render(); status.textContent = "";
    } catch (cause) { error.textContent = cause.message; error.hidden = false; status.textContent = "Não foi possível carregar as vaquinhas."; }
    finally { list.setAttribute("aria-busy","false"); }
  }

  function toLocalInput(value) {
    const date = new Date(value); if (!Number.isFinite(date.getTime())) return "";
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0,16);
  }

  function open(item = null) {
    current = item; currentImage = safeImageUrl(item?.imageUrl); form.reset(); image.value = ""; feedback.textContent = "";
    document.getElementById("form-campanha-titulo").textContent = item ? "Editar vaquinha" : "Criar vaquinha";
    document.getElementById("campanha-titulo").value = item?.title || "";
    document.getElementById("campanha-descricao").value = item?.description || "";
    meta.value = item ? money(item.goalNetCents) : "";
    document.getElementById("campanha-prazo").value = item?.deadline ? toLocalInput(item.deadline) : "";
    publishControl.hidden = Boolean(item && !["DRAFT","ACTIVE"].includes(item.state));
    publish.checked = item ? item.state === "ACTIVE" : true;
    metaError.hidden = true; meta.removeAttribute("aria-invalid"); renderPreview();
    if (!dialog.open) dialog.showModal();
    document.getElementById("campanha-titulo").focus();
  }

  async function changeState(item, next, confirmEnd = false) {
    if (confirmEnd && !window.confirm("Encerrar esta vaquinha? O histórico será preservado.")) return;
    const buttons = list.querySelectorAll("button"); buttons.forEach(button => { button.disabled = true; });
    status.textContent = "Atualizando vaquinha…";
    try {
      const response = await fetchAutenticado(editPath(item.id), { method:"PUT", headers:{"Content-Type":"application/json"},
        body:JSON.stringify({title:item.title,description:item.description,imageUrl:item.imageUrl,goalNetCents:Number(item.goalNetCents),deadline:item.deadline,state:next}) });
      await json(response); await load();
    } catch (cause) { error.textContent = cause.message; error.hidden = false; status.textContent = "Não foi possível atualizar a vaquinha."; }
    finally { buttons.forEach(button => { if (button.isConnected) button.disabled = false; }); }
  }

  document.getElementById("criar-campanha-doacao").addEventListener("click", () => open());
  document.getElementById("cancelar-campanha-doacao").addEventListener("click", () => dialog.close("cancelar"));
  meta.addEventListener("blur", () => { const cents = campaignCurrencyToCents(meta.value); if (cents !== null) meta.value = money(cents); });
  meta.addEventListener("input", () => { metaError.hidden = true; meta.removeAttribute("aria-invalid"); });
  image.addEventListener("change", () => renderPreview(image.files?.[0] || null));
  removeImage.addEventListener("click", () => { image.value = ""; currentImage = null; renderPreview(); });

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (form.dataset.salvando === "true") return;
    if (!form.reportValidity()) return;
    const cents = campaignCurrencyToCents(meta.value);
    if (cents === null || cents < 500) {
      metaError.textContent = "Informe uma meta de pelo menos R$ 5,00, com até duas casas decimais.";
      metaError.hidden = false; meta.setAttribute("aria-invalid","true"); meta.focus(); return;
    }
    form.dataset.salvando = "true";
    const button = document.getElementById("salvar-campanha-doacao");
    button.disabled = true; feedback.textContent = "Salvando vaquinha…";
    try {
      const deadlineValue = document.getElementById("campanha-prazo").value;
      const payload = { title:document.getElementById("campanha-titulo").value.trim(), description:document.getElementById("campanha-descricao").value.trim(), imageUrl:currentImage,
        goalNetCents:cents, deadline:deadlineValue ? new Date(deadlineValue).toISOString() : null,
        state:publishControl.hidden ? current.state : publish.checked ? "ACTIVE" : "DRAFT" };
      const data = new FormData(); data.append("dados", new Blob([JSON.stringify(payload)], { type:"application/json" }));
      if (image.files?.[0]) data.append("imagem", image.files[0], image.files[0].name);
      const path = current ? `${editPath(current.id)}/with-image` : `/donation-campaigns/organizations/${oscId}/with-image`;
      const response = await fetchAutenticado(path,{method:current?"PUT":"POST",body:data});
      await json(response);
      dialog.close("salvo"); current = null; currentImage = null; await load();
    } catch (cause) {
      feedback.textContent = cause.message || "Não foi possível salvar a vaquinha agora. Seus dados foram mantidos; tente novamente.";
    } finally { form.dataset.salvando = "false"; button.disabled = false; }
  });

  dialog.addEventListener("click", event => { if (event.target === dialog && form.dataset.salvando !== "true") dialog.close("fora"); });
  dialog.addEventListener("cancel", event => { if (form.dataset.salvando === "true") event.preventDefault(); });
  dialog.addEventListener("close", () => { clearPreviewUrl(); });
  void load();
}
