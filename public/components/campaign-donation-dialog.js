import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado } from "./api.js";
import { campaignCurrencyToCents, campaignMoney } from "./campaign-money.js";
import { urlImagemSegura } from "./ong-content.js";

const ID = "campaign-donation-dialog";
const TEMPLATE = `<dialog id="${ID}" class="catalogo-campanha-dialog" aria-labelledby="catalogo-campanha-titulo" aria-describedby="catalogo-campanha-descricao">
  <div class="catalogo-campanha-dialog__layout">
    <div class="catalogo-campanha-dialog__media"><img data-campaign="image" alt="" hidden></div>
    <article class="catalogo-campanha-dialog__content">
      <header class="catalogo-campanha-dialog__header"><div class="catalogo-campanha-dialog__ngo"><img data-campaign="logo" alt="" hidden><span data-campaign="ngo"></span></div><button data-campaign="close" type="button" aria-label="Fechar detalhes da vaquinha">×</button></header>
      <h2 id="catalogo-campanha-titulo" data-campaign="title"></h2><p id="catalogo-campanha-descricao" data-campaign="description" class="catalogo-campanha-dialog__description"></p>
      <section class="catalogo-campanha-impacto" aria-label="Progresso da vaquinha"><div><strong data-campaign="raised"></strong><span> arrecadados</span></div><progress data-campaign="progress" max="100" value="0"></progress><p>Meta líquida da ONG: <strong data-campaign="goal"></strong></p><p data-campaign="deadline" hidden></p></section>
      <section class="catalogo-campanha-contribuir" aria-labelledby="campaign-donation-heading"><h3 id="campaign-donation-heading">Contribuir para esta vaquinha</h3>
        <p data-campaign="quote-status" class="catalogo-campanha-availability" role="status" aria-live="polite">Verificando disponibilidade…</p>
        <details class="catalogo-campanha-info"><summary>Informações sobre pagamentos e tarifas</summary><p data-campaign="payment-note" class="catalogo-campanha-availability-note"></p><p class="catalogo-campanha-disclosure">O valor líquido e os custos são calculados pelo servidor. A estimativa de cartão é provisória, não representa tarifa individual confirmada e não garante margem líquida. Nenhum pagamento live está habilitado.</p></details>
        <label data-campaign="amount-label" for="campaign-donation-amount">Valor líquido que a ONG deve receber (R$)</label>
        <input data-campaign="amount" id="campaign-donation-amount" type="text" inputmode="decimal" placeholder="R$ 0,00" autocomplete="off">
        <button data-campaign="quote" class="catalogo-aplicar" type="button" disabled>Calcular total e revisar custos</button>
        <div data-campaign="breakdown" class="catalogo-campanha-cotacao" hidden></div>
        <label data-campaign="confirmation" class="catalogo-campanha-confirmacao" hidden><input data-campaign="confirm" type="checkbox"> Confirmo o valor total e a divisão dos custos acima.</label>
        <button data-campaign="pay" class="catalogo-campanha-pagar" type="button" hidden>Continuar para checkout de teste</button>
        <a data-campaign="login" class="catalogo-campanha-login" href="/login" hidden>Entrar para continuar</a>
      </section>
    </article>
  </div>
</dialog>`;

/** One shared campaign detail, quote and checkout UI for catalog and homepage. */
export function createCampaignDonationDialog() {
  let dialog = document.getElementById(ID);
  if (!dialog) {
    document.body.insertAdjacentHTML("beforeend", TEMPLATE);
    dialog = document.getElementById(ID);
  }
  const $ = key => dialog.querySelector(`[data-campaign="${key}"]`);
  const API = getAPI_URL().replace(/\/+$/, "");
  let campaign = null;
  let quote = null;
  let opener = null;
  let openerScrollY = 0;
  let availabilityVersion = 0;
  let lookupVersion = 0;
  let checkoutAvailable = false;
  let modalHistoryEntry = false;

  function campaignParam() { return new URL(location.href).searchParams.get("campanha"); }
  function updateUrl(id, cents = null, replace = false) {
    const url = new URL(location.href);
    if (id) url.searchParams.set("campanha", String(id)); else url.searchParams.delete("campanha");
    url.searchParams.delete("valor");
    if (cents && cents >= 500) url.searchParams.set("valorCentavos", String(cents)); else url.searchParams.delete("valorCentavos");
    const state = { ...(history.state || {}), campaignDonationDialog: Boolean(id) };
    history[replace ? "replaceState" : "pushState"](state, "", url);
  }
  function clearQuote() {
    quote = null; $("breakdown").replaceChildren(); $("breakdown").hidden = true;
    $("confirmation").hidden = true; $("confirm").checked = false;
    $("pay").hidden = true; $("pay").disabled = false;
  }
  function restoreFocus() {
    const target = opener;
    const y = openerScrollY;
    opener = null;
    requestAnimationFrame(() => {
      target?.focus?.({ preventScroll: true });
      requestAnimationFrame(() => window.scrollTo(0, y));
    });
  }
  function finishClose() {
    if (dialog.open) dialog.close();
    campaign = null; quote = null; checkoutAvailable = false;
    availabilityVersion += 1; lookupVersion += 1;
    restoreFocus();
  }
  function closeByUser() {
    if (!campaign && !dialog.open) return;
    const goBack = modalHistoryEntry;
    modalHistoryEntry = false;
    finishClose();
    if (goBack) history.back();
    else if (campaignParam()) updateUrl(null, null, true);
  }
  function saveEntryForDeepLink() {
    if (modalHistoryEntry || !campaignParam()) return;
    const url = new URL(location.href);
    const selected = url.searchParams.get("campanha");
    const cents = url.searchParams.get("valorCentavos");
    url.searchParams.delete("campanha"); url.searchParams.delete("valorCentavos"); url.searchParams.delete("valor");
    history.replaceState({ ...(history.state || {}), campaignDonationDialog: false }, "", url);
    const modalUrl = new URL(location.href);
    modalUrl.searchParams.set("campanha", selected);
    if (cents) modalUrl.searchParams.set("valorCentavos", cents);
    history.pushState({ ...(history.state || {}), campaignDonationDialog: true }, "", modalUrl);
    modalHistoryEntry = true;
  }

  function open(item, trigger = null, { pushUrl = true, fromUrl = false, fromPop = false } = {}) {
    if (!item?.id) return;
    if (campaign?.id === item.id && dialog.open) return;
    campaign = item; quote = null; checkoutAvailable = false;
    if (trigger) { opener = trigger; openerScrollY = window.scrollY; }
    else if (!opener) openerScrollY = window.scrollY;
    const image = $("image");
    const imageUrl = item.imageUrl && urlImagemSegura(item.imageUrl) ? item.imageUrl : "";
    image.hidden = !imageUrl;
    if (imageUrl) { image.src = imageUrl; image.alt = `Imagem da vaquinha ${item.title || ""}`; } else image.removeAttribute("src");
    const logo = $("logo");
    const logoUrl = item.ngoLogo && urlImagemSegura(item.ngoLogo) ? item.ngoLogo : "";
    logo.hidden = !logoUrl;
    if (logoUrl) { logo.src = logoUrl; logo.alt = `Logotipo de ${item.ngoName || "ONG"}`; } else logo.removeAttribute("src");
    $("ngo").textContent = item.ngoName || "ONG";
    $("title").textContent = item.title || "Vaquinha";
    $("description").textContent = item.description || "";
    $("raised").textContent = campaignMoney(item.raisedNetCents);
    $("goal").textContent = campaignMoney(item.goalNetCents);
    const progress = $("progress");
    progress.max = Math.max(1, Number(item.goalNetCents) || 1);
    progress.value = Math.min(progress.max, Math.max(0, Number(item.raisedNetCents) || 0));
    progress.setAttribute("aria-label", `${campaignMoney(item.raisedNetCents)} arrecadados de ${campaignMoney(item.goalNetCents)} líquidos`);
    const deadline = $("deadline");
    deadline.hidden = !item.deadline;
    deadline.textContent = item.deadline ? `Prazo: ${new Date(item.deadline).toLocaleDateString("pt-BR")}` : "";
    const params = new URL(location.href).searchParams;
    const cents = Number(params.get("valorCentavos"));
    $("amount").value = Number.isSafeInteger(cents) && cents >= 500 ? campaignMoney(cents) : params.get("valor") || "";
    $("quote-status").textContent = "Verificando a disponibilidade de cotação…";
    $("payment-note").textContent = ""; $("login").hidden = true;
    $("amount-label").hidden = false; $("amount").hidden = false; $("amount").disabled = false;
    $("quote").hidden = false; $("quote").disabled = true;
    clearQuote();
    if (pushUrl) { updateUrl(item.id, campaignCurrencyToCents($("amount").value), false); modalHistoryEntry = true; }
    else if (fromUrl && !fromPop) saveEntryForDeepLink();
    if (!dialog.open) dialog.showModal();
    $("close").focus();
    const version = ++availabilityVersion;
    fetch(`${API}/donation-campaigns/${encodeURIComponent(item.id)}/payment-availability`, { headers: { Accept: "application/json" }, cache: "no-store" })
      .then(async response => { const body = await response.json(); if (!response.ok) throw new Error(body.detail || "Unavailable"); return body; })
      .then(result => {
        if (version !== availabilityVersion || campaign?.id !== item.id) return;
        const canQuote = Boolean(result.quoteAvailable);
        $("quote").disabled = !canQuote; $("quote").hidden = !canQuote;
        $("amount").disabled = !canQuote; $("amount").hidden = !canQuote; $("amount-label").hidden = !canQuote;
        $("quote-status").textContent = result.message || "Confira a disponibilidade antes de contribuir.";
        $("payment-note").textContent = result.pricingNote || "A cotação e a confirmação dependem das tarifas e da configuração de teste disponíveis.";
        checkoutAvailable = Boolean(result.checkoutAvailable);
      }).catch(() => {
        if (version !== availabilityVersion) return;
        $("quote-status").textContent = "Não foi possível confirmar a disponibilidade da cotação. Tente novamente mais tarde.";
        $("quote").disabled = true; $("quote").hidden = true;
        $("amount").disabled = true; $("amount").hidden = true; $("amount-label").hidden = true;
      });
  }

  async function openById(id, options = {}) {
    const version = ++lookupVersion;
    try {
      const response = await fetch(`${API}/donation-campaigns/${encodeURIComponent(id)}`, { headers: { Accept: "application/json" }, cache: "no-store" });
      if (!response.ok) throw new Error("Campaign unavailable");
      const item = await response.json();
      if (version === lookupVersion && (!options.fromUrl || campaignParam() === String(id))) open(item, options.trigger || null, { pushUrl: false, fromUrl: options.fromUrl !== false, fromPop: Boolean(options.fromPop) });
    } catch {
      if (version === lookupVersion && options.fromUrl) {
        const url = new URL(location.href); url.searchParams.delete("campanha"); url.searchParams.delete("valorCentavos");
        history.replaceState({ ...(history.state || {}), campaignDonationDialog: false }, "", url);
      }
    }
  }

  $("close").addEventListener("click", closeByUser);
  dialog.addEventListener("keydown", event => {
    if (event.key === "Escape") { event.preventDefault(); closeByUser(); }
  });
  dialog.addEventListener("cancel", event => { event.preventDefault(); closeByUser(); });
  dialog.addEventListener("click", event => { if (event.target === dialog) closeByUser(); });
  $("amount").addEventListener("input", () => {
    clearQuote(); $("login").hidden = true;
    if (campaign) updateUrl(campaign.id, campaignCurrencyToCents($("amount").value), true);
  });
  $("quote").addEventListener("click", async () => {
    if (!campaign) return;
    const cents = campaignCurrencyToCents($("amount").value);
    if (cents === null || cents < 500) { $("quote-status").textContent = "Informe o valor líquido que deseja destinar à ONG (mínimo de R$ 5,00)."; $("amount").focus(); return; }
    const quotedCampaign = campaign;
    $("quote").disabled = true; clearQuote(); $("login").hidden = true; $("quote-status").textContent = "Calculando a estimativa…";
    try {
      const response = await fetchAutenticado(`/donation-campaigns/${quotedCampaign.id}/quotes`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ netAmountCents: cents, paymentMethod: "CARD" }) });
      let body = {}; try { body = await response.json(); } catch {}
      if (response.status === 401 || response.status === 403) {
        $("quote-status").textContent = "Entre na sua conta para calcular a cotação. O valor informado foi mantido.";
        $("login").href = `/login?retorno=${encodeURIComponent(`${location.pathname}${location.search}`)}`; $("login").hidden = false; return;
      }
      if (!response.ok) { $("quote-status").textContent = response.status === 404 ? "Esta vaquinha não está disponível para contribuições." : response.status === 503 ? (body.detail || body.message || "A cotação está indisponível neste ambiente.") : response.status === 400 ? (body.detail || "Confira o valor e tente novamente.") : "Não foi possível calcular a cotação agora. Tente novamente."; return; }
      quote = body;
      const rows = [["Valor líquido para a ONG",body.netAmountCents],["Custos de processamento estimados",body.processingFeeCents],["Parcela da plataforma",body.platformFeeCents],["Outros custos conhecidos",body.additionalKnownFeeCents],["Total a pagar",body.totalCents]];
      $("breakdown").replaceChildren();
      for (const [label,value] of rows) { const row = document.createElement("div"); row.className = "catalogo-campanha-cotacao-linha"; const name = document.createElement("span"); name.textContent = label; const price = document.createElement("strong"); price.textContent = campaignMoney(value); row.append(name,price); $("breakdown").append(row); }
      const note = document.createElement("p"); note.className = "catalogo-campanha-cotacao-nota"; note.textContent = body.pricingNote || body.breakdown?.assumption || "Estimativa baseada na regra de teste configurada."; $("breakdown").append(note); $("breakdown").hidden = false;
      if (checkoutAvailable) { $("confirmation").hidden = false; $("pay").hidden = false; $("quote-status").textContent = "Revise a divisão dos valores e confirme antes de continuar."; }
      else $("quote-status").textContent = "A cotação foi calculada. O pagamento está desativado neste ambiente; você não será cobrado.";
    } catch {
      $("quote-status").textContent = "Não foi possível confirmar sua sessão. Entre na sua conta ou tente novamente.";
      $("login").href = `/login?retorno=${encodeURIComponent(`${location.pathname}${location.search}`)}`; $("login").hidden = false;
    } finally { $("quote").disabled = false; }
  });
  $("pay").addEventListener("click", async () => {
    if (!quote || !$("confirm").checked || !checkoutAvailable) { $("quote-status").textContent = "Confirme a cotação e verifique a disponibilidade para continuar."; return; }
    $("pay").disabled = true; $("quote-status").textContent = "Preparando checkout seguro…";
    try {
      const response = await fetchAutenticado(`/donation-campaigns/quotes/${encodeURIComponent(quote.quoteId)}/checkout`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ confirmed: true }) });
      let body = {}; try { body = await response.json(); } catch {}
      if (response.status === 401 || response.status === 403) { $("quote-status").textContent = "Entre na sua conta para continuar. O valor líquido foi mantido."; $("login").href = `/login?retorno=${encodeURIComponent(`${location.pathname}${location.search}`)}`; $("login").hidden = false; return; }
      if (!response.ok) throw new Error(body.detail || body.message || "O checkout não está disponível neste ambiente.");
      location.assign(body.url);
    } catch (error) { $("quote-status").textContent = error.message || "O checkout não está disponível neste ambiente."; }
    finally { $("pay").disabled = false; }
  });

  window.addEventListener("popstate", () => {
    const id = campaignParam();
    if (!id) { lookupVersion += 1; modalHistoryEntry = false; if (dialog.open) finishClose(); return; }
    modalHistoryEntry = false;
    if (campaign?.id === id && dialog.open) return;
    void openById(id, { fromUrl: false, fromPop: true });
  });

  const initialId = campaignParam();
  if (initialId) void openById(initialId, { fromUrl: true });
  return { open, openById };
}
