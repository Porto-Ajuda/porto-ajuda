import { getAPI_URL } from "./modoBack.js";
import { urlImagemSegura } from "./ong-content.js";
import { fetchAutenticado } from "./api.js";
import { distanceKm, geocodingFailureMessage, parseCatalogLocation, validCatalogCoordinates } from "./catalog-location.js";

const API = getAPI_URL();
const params = new URLSearchParams(location.search);
const category = (params.get("categoria") || "ongs").toLowerCase();
const CONFIG = {
  ongs: { title: "Explorar ONGs", description: "Conheça organizações ativas e publicamente visíveis.", endpoint: "/osc/list", kind: "ongs", items: "content" },
  publicacoes: { title: "Explorar Publicações", description: "Publicações públicas recentes de organizações parceiras.", endpoint: "/post/catalogo/publicacoes", kind: "posts", items: "content" },
  voluntariado: { title: "Explorar Voluntariado", description: "Oportunidades abertas e disponíveis para participar.", endpoint: "/post/catalogo/voluntariados", kind: "volunteer", items: "content" },
  doacoes: { title: "Explorar Doações", description: "Campanhas públicas verificadas do Porto Ajuda.", kind: "donations" }
};
const config = CONFIG[category] || CONFIG.ongs;
const $ = id => document.getElementById(id);
const PAGE_SIZE = 6;
const API_PAGE_SIZE = 24;
const MAX_RECORDS = 120;
const causes = ["EDUCACAO", "SAUDE", "MEIO_AMBIENTE", "ANIMAIS", "INCLUSAO_SOCIAL", "ESPORTE", "CULTURA", "COMBATE_POBREZA"];
let sourceItems = [];
let resultItems = [];
let resultPage = 0;
let locationPoint = null;
let locationCity = "";
let locationState = "";
let locationLabel = "";
let locationSource = "";
let requestVersion = 0;
let sourceNextPage = 0;
let sourceTotalPages = 0;
let sourceTotalElements = 0;

function text(value) { return String(value ?? "").trim(); }
function norm(value) { return text(value).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR"); }
function itemDate(item) { return new Date(item.dataCriacao || item.dataAtualizacao || 0).getTime() || 0; }
function periodDate(item) { return config.kind === "volunteer" ? new Date(item.vaga?.dataInicio || item.vaga?.dataFim || item.dataCriacao || 0).getTime() || 0 : itemDate(item); }
function formatarLocal(city, state) { return [city, state].filter(Boolean).join(", "); }
function atualizarIndicadorLocal() {
  const indicator = $("catalogo-local-indicador");
  indicator.textContent = locationLabel ? `${locationLabel}${locationPoint && config.kind !== "volunteer" ? ` · ${$("catalogo-distancia").value} km` : ""}` : "Qualquer lugar";
  indicator.classList.toggle("catalogo-local-indicator-active", Boolean(locationLabel));
  $("catalogo-remover-localizacao").hidden = !locationLabel;
}
function setUpPage() {
  document.title = `${config.title} | Porto Ajuda`;
  $("catalogo-titulo").textContent = config.title;
  $("catalogo-descricao").textContent = config.description;
  if (config.kind === "donations") {
    $("catalogo-filtros").hidden = true;
    $("catalogo-status").hidden = true;
    $("catalogo-descobertas").hidden = true;
    $("catalogo-doacoes-vazio").hidden = false;
    return;
  }
  $("catalogo-filtro-a-label").firstChild.textContent = config.kind === "ongs" ? "Causa social" : config.kind === "volunteer" ? "Modalidade" : "Causa social";
  const filter = $("catalogo-filtro-a");
  filter.replaceChildren(new Option(config.kind === "volunteer" ? "Todas as modalidades" : "Todas as causas", ""));
  const choices = config.kind === "volunteer" ? [["PRESENCIAL", "Presencial"], ["REMOTO", "Remoto"], ["HIBRIDO", "Híbrido"]] : causes.map(value => [value, value.replaceAll("_", " ").toLocaleLowerCase("pt-BR")]);
  choices.forEach(([value, label]) => filter.add(new Option(label, value)));
  if (config.kind === "posts" || config.kind === "volunteer") {
    $("catalogo-ong-label").hidden = false;
    $("catalogo-data-inicio-label").hidden = false;
    $("catalogo-data-fim-label").hidden = false;
  }
  if (config.kind === "ongs") $("catalogo-ods-label").hidden = false;
  if (config.kind === "volunteer") $("catalogo-vagas-label").hidden = false;
  const stateSelect = $("catalogo-estado");
  for (const [uf, name] of [["AC","Acre"],["AL","Alagoas"],["AP","Amapá"],["AM","Amazonas"],["BA","Bahia"],["CE","Ceará"],["DF","Distrito Federal"],["ES","Espírito Santo"],["GO","Goiás"],["MA","Maranhão"],["MT","Mato Grosso"],["MS","Mato Grosso do Sul"],["MG","Minas Gerais"],["PA","Pará"],["PB","Paraíba"],["PR","Paraná"],["PE","Pernambuco"],["PI","Piauí"],["RJ","Rio de Janeiro"],["RN","Rio Grande do Norte"],["RS","Rio Grande do Sul"],["RO","Rondônia"],["RR","Roraima"],["SC","Santa Catarina"],["SP","São Paulo"],["SE","Sergipe"],["TO","Tocantins"]]) stateSelect.add(new Option(`${name} (${uf})`, uf));
  if (config.kind === "volunteer") {
    $("catalogo-usar-localizacao").hidden = true;
    $("catalogo-distancia-label").hidden = true;
    $("catalogo-estado-label").hidden = true;
  }
  if (config.kind === "ongs") $("catalogo-ordem").add(new Option("Nome", "nome"));
  hydrateFiltersFromUrl();
}
function hydrateFiltersFromUrl() {
  $("catalogo-termo").value = params.get("q") || "";
  $("catalogo-filtro-a").value = params.get("filtro") || "";
  $("catalogo-local").value = params.get("local") || "";
  $("catalogo-estado").value = params.get("estado") || "";
  $("catalogo-distancia").value = params.get("distancia") || "10";
  $("catalogo-ordem").value = params.get("ordem") || "recentes";
  $("catalogo-data-inicio").value = params.get("de") || "";
  $("catalogo-data-fim").value = params.get("ate") || "";
  $("catalogo-ods").value = params.get("ods") || "";
  $("catalogo-vagas").value = params.get("vagas") || "0";
}
function apiPage(page) {
  const url = new URL(config.endpoint, API);
  url.searchParams.set("page", String(page));
  url.searchParams.set("size", String(API_PAGE_SIZE));
  return fetch(url, { headers: { Accept: "application/json" }, cache: "no-store" }).then(async response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  });
}
async function loadBounded() {
  const version = ++requestVersion;
  sourceItems = [];
  sourceNextPage = 0;
  sourceTotalPages = 0;
  sourceTotalElements = 0;
  setStatus("Carregando conteúdo público…");
  $("catalogo-repetir").hidden = true;
  $("catalogo-carregar-mais").hidden = true;
  try {
    const first = await apiPage(0);
    const all = [...(first.content || [])];
    sourceTotalPages = Number(first.totalPages) || 1;
    sourceTotalElements = Number(first.totalElements) || all.length;
    sourceNextPage = 1;
    const batchLimit = Math.ceil(MAX_RECORDS / API_PAGE_SIZE);
    for (let page = 1; page < Math.min(sourceTotalPages, batchLimit); page += 1) {
      const next = await apiPage(page);
      all.push(...(next.content || []));
      sourceNextPage = page + 1;
      if (all.length >= MAX_RECORDS || !(next.content || []).length) break;
    }
    if (version !== requestVersion) return;
    sourceItems = all.slice(0, MAX_RECORDS);
    $("catalogo-carregar-mais").hidden = sourceNextPage >= sourceTotalPages || sourceItems.length >= sourceTotalElements;
    if (config.kind === "posts") {
      $("catalogo-filtro-a").value = params.get("filtro") || "";
      const select = $("catalogo-ong");
      select.replaceChildren(new Option("Todas as ONGs", ""));
      [...new Set(sourceItems.map(item => text(item.nomeOsc)).filter(Boolean))].sort((a, b) => a.localeCompare(b, "pt-BR")).forEach(name => select.add(new Option(name, name)));
      select.value = params.get("ong") || "";
    } else if (config.kind === "volunteer") {
      const select = $("catalogo-ong");
      select.replaceChildren(new Option("Todas as ONGs", ""));
      [...new Set(sourceItems.map(item => text(item.nomeOsc)).filter(Boolean))].sort((a, b) => a.localeCompare(b, "pt-BR")).forEach(name => select.add(new Option(name, name)));
      select.value = params.get("ong") || "";
    }
    if (config.kind === "ongs") {
      const ods = $("catalogo-ods");
      const selectedOds = params.get("ods") || "";
      ods.replaceChildren(new Option("Todas as ODS", ""));
      [...new Set(sourceItems.flatMap(item => item.ods || []).map(String))].sort((a, b) => Number(a) - Number(b)).forEach(value => ods.add(new Option(`ODS ${value}`, value)));
      ods.value = selectedOds;
    }
    await restaurarLocalizacaoDaUrl();
  } catch (error) {
    if (version !== requestVersion) return;
    setStatus("Não foi possível carregar o catálogo agora.");
    $("catalogo-repetir").hidden = false;
  }
}
async function loadMoreBounded() {
  const button = $("catalogo-carregar-mais");
  if (button.disabled || sourceNextPage >= sourceTotalPages) return;
  button.disabled = true;
  button.textContent = "Carregando…";
  try {
    const stopAt = Math.min(sourceTotalPages, sourceNextPage + Math.ceil(MAX_RECORDS / API_PAGE_SIZE));
    while (sourceNextPage < stopAt && sourceItems.length < sourceTotalElements) {
      const page = await apiPage(sourceNextPage);
      sourceItems.push(...(page.content || []));
      sourceNextPage += 1;
      if (!(page.content || []).length) break;
    }
    sourceItems = sourceItems.slice(0, sourceTotalElements);
    button.hidden = sourceNextPage >= sourceTotalPages || sourceItems.length >= sourceTotalElements;
    applyFilters(false);
  } catch {
    setStatus("Não foi possível carregar mais resultados.");
  } finally {
    button.disabled = false;
    button.textContent = "Carregar mais resultados";
  }
}
function setStatus(value) { $("catalogo-status").textContent = value; }
function searchable(item) {
  return config.kind === "ongs"
    ? [item.nome, item.cidade, item.estado, item.causaPrincipal, ...(item.causas || []), ...(item.causasSecundarias || []), ...(item.ods || []).map(String)].join(" ")
    : [item.titulo, item.conteudo, item.nomeOsc, item.causaPrincipalOsc, item.cidadeOsc, item.estadoOsc, item.tipo, item.vaga?.cidade, item.vaga?.bairro, item.vaga?.modalidadeVoluntariado, item.vaga?.requisitos].join(" ");
}
function matchesFilter(item, term, selected, local) {
  if (term && !norm(searchable(item)).includes(term)) return false;
  if (selected) {
    if (config.kind === "volunteer" && item.vaga?.modalidadeVoluntariado !== selected) return false;
    if (config.kind === "ongs" && ![item.causaPrincipal, ...(item.causas || []), ...(item.causasSecundarias || [])].includes(selected)) return false;
    if (config.kind === "posts" && item.causaPrincipalOsc !== selected) return false;
  }
  if ($("catalogo-ong").value && item.nomeOsc !== $("catalogo-ong").value) return false;
  if (config.kind === "ongs" && $("catalogo-ods").value && !(item.ods || []).includes(Number($("catalogo-ods").value))) return false;
  if (config.kind === "volunteer" && Number(item.vaga?.quantidadeVagas || 0) < Number($("catalogo-vagas").value || 0)) return false;
  if (local) {
    const place = config.kind === "ongs" ? [item.cidade, item.estado, item.pais].join(" ") : [item.vaga?.cidade || item.cidadeOsc, item.vaga?.bairro, item.estadoOsc].join(" ");
    const remoto = config.kind === "volunteer" && item.vaga?.modalidadeVoluntariado === "REMOTO";
    if (!remoto && !norm(place).includes(local)) return false;
  }
  const date = periodDate(item);
  if ($("catalogo-data-inicio").value && date < new Date(`${$("catalogo-data-inicio").value}T00:00:00`).getTime()) return false;
  if ($("catalogo-data-fim").value && date > new Date(`${$("catalogo-data-fim").value}T23:59:59`).getTime()) return false;
  if (locationPoint && (config.kind === "ongs" || config.kind === "posts")) {
    const latitude = Number(config.kind === "ongs" ? item.latitude : item.latitudeOsc);
    const longitude = Number(config.kind === "ongs" ? item.longitude : item.longitudeOsc);
    const coordinates = validCatalogCoordinates(latitude, longitude);
    if (!coordinates) return false;
    if (distanceKm(locationPoint, coordinates) > Number($("catalogo-distancia").value)) return false;
  }
  return true;
}
function applyFilters(updateUrl = true) {
  const term = norm($("catalogo-termo").value);
  const selected = $("catalogo-filtro-a").value;
  const local = norm([locationCity, locationState].filter(Boolean).join(" "));
  resultItems = sourceItems.filter(item => matchesFilter(item, term, selected, local));
  const order = $("catalogo-ordem").value;
  resultItems.sort((a, b) => {
    if (order === "nome") return text(a.nome).localeCompare(text(b.nome), "pt-BR");
    if (order === "relevancia" && term) {
      const titleA = norm(a.nome || a.titulo), titleB = norm(b.nome || b.titulo);
      const scoreA = titleA.startsWith(term) ? 2 : titleA.includes(term) ? 1 : 0;
      const scoreB = titleB.startsWith(term) ? 2 : titleB.includes(term) ? 1 : 0;
      if (scoreA !== scoreB) return scoreB - scoreA;
    }
    if (locationPoint && (config.kind === "ongs" || config.kind === "posts")) {
      const ca = validCatalogCoordinates(config.kind === "ongs" ? a.latitude : a.latitudeOsc, config.kind === "ongs" ? a.longitude : a.longitudeOsc);
      const cb = validCatalogCoordinates(config.kind === "ongs" ? b.latitude : b.latitudeOsc, config.kind === "ongs" ? b.longitude : b.longitudeOsc);
      const da = ca ? distanceKm(locationPoint, ca) : Number.POSITIVE_INFINITY;
      const db = cb ? distanceKm(locationPoint, cb) : Number.POSITIVE_INFINITY;
      if (da !== db) return da - db;
    }
    return itemDate(b) - itemDate(a);
  });
  resultPage = 0;
  if (updateUrl) {
    const url = new URL(location.href);
    for (const [key, value] of [["q", $("catalogo-termo").value.trim()], ["filtro", selected], ["ong", $("catalogo-ong").value], ["ods", $("catalogo-ods").value], ["vagas", $("catalogo-vagas").value === "0" ? "" : $("catalogo-vagas").value], ["local", locationSource.startsWith("current") ? "atual" : locationLabel], ["estado", locationSource.startsWith("current") ? "" : locationState], ["distancia", locationSource.startsWith("current") || (locationSource === "manual" && locationPoint && config.kind !== "volunteer") ? $("catalogo-distancia").value : ""], ["ordem", order], ["de", $("catalogo-data-inicio").value], ["ate", $("catalogo-data-fim").value]]) value ? url.searchParams.set(key, value) : url.searchParams.delete(key);
    history.pushState({}, "", url);
  }
  renderResultPage();
  renderDiscoveryRows();
  atualizarIndicadorLocal();
}
function safeImage(src) { return src && urlImagemSegura(src) ? src : ""; }
function createCard(item) {
  const isNgo = config.kind === "ongs";
  const card = document.createElement(isNgo ? "a" : "button");
  if (isNgo) { card.href = `/ong-profile?id=${encodeURIComponent(item.id)}`; }
  else { card.type = "button"; card.setAttribute("aria-haspopup", "dialog"); }
  card.className = "catalogo-card";
  card.setAttribute("role", "listitem");
  const media = document.createElement("div"); media.className = "catalogo-card-media";
  const imageUrl = safeImage(isNgo ? item.logo : item.imagemUrl);
  if (imageUrl) { const image = document.createElement("img"); image.src = imageUrl; image.alt = ""; image.loading = "lazy"; image.referrerPolicy = "no-referrer"; media.append(image); }
  else { const fallback = document.createElement("span"); fallback.className = "catalogo-card-fallback"; fallback.textContent = isNgo ? "ONG parceira" : text(item.titulo) || "Conteúdo público"; media.append(fallback); }
  const info = document.createElement("div"); info.className = "catalogo-card-info";
  const title = document.createElement("strong"); title.textContent = isNgo ? text(item.nome) : text(item.titulo) || "Conteúdo público"; info.append(title);
  const sub = document.createElement("span"); sub.textContent = isNgo ? [item.causaPrincipal?.replaceAll("_", " "), item.cidade, item.estado].filter(Boolean).join(" · ") : [item.nomeOsc, config.kind === "posts" ? item.causaPrincipalOsc?.replaceAll("_", " ") : ""].filter(Boolean).join(" · "); info.append(sub);
  if (config.kind === "volunteer" && item.vaga) {
    const details = document.createElement("span"); details.textContent = [item.vaga.modalidadeVoluntariado?.replaceAll("_", " "), item.vaga.cidade || item.cidadeOsc, item.vaga.quantidadeVagas > 0 ? `${item.vaga.quantidadeVagas} vagas` : "", item.vaga.dataFim ? `até ${new Date(`${item.vaga.dataFim}T00:00:00`).toLocaleDateString("pt-BR")}` : ""].filter(Boolean).join(" · "); info.append(details);
  } else if (!isNgo && item.dataCriacao) { const date = document.createElement("time"); date.dateTime = item.dataCriacao; date.textContent = new Date(item.dataCriacao).toLocaleDateString("pt-BR"); info.append(date); }
  card.append(media, info);
  if (!isNgo) card.addEventListener("click", () => window.abrirModalConteudoPublico?.(item, config.kind === "volunteer" ? "voluntariado" : "publicacao", card));
  return card;
}
function renderResultPage() {
  const start = resultPage * PAGE_SIZE;
  const items = resultItems.slice(start, start + PAGE_SIZE);
  const section = $("catalogo-resultados"); section.hidden = false;
  const grid = $("catalogo-grade"); grid.replaceChildren(...items.map(createCard));
  const locationApplied = Boolean(locationPoint || (locationSource === "manual" && config.kind === "volunteer"));
  const filtered = Boolean($("catalogo-termo").value || $("catalogo-filtro-a").value || $("catalogo-ong").value || $("catalogo-ods").value || Number($("catalogo-vagas").value) || locationApplied || $("catalogo-data-inicio").value || $("catalogo-data-fim").value);
  $("catalogo-resultados-titulo").textContent = filtered ? `Resultados (${resultItems.length})` : "Conteúdo recente";
  const totalPages = Math.ceil(resultItems.length / PAGE_SIZE);
  $("catalogo-pagina").textContent = totalPages ? `Página ${resultPage + 1} de ${totalPages}` : "";
  $("catalogo-anterior").disabled = resultPage <= 0;
  $("catalogo-proximo").disabled = resultPage + 1 >= totalPages;
  setStatus(resultItems.length ? `Exibindo ${resultItems.length} resultados entre ${sourceItems.length} itens públicos carregados de ${sourceTotalElements}.` : "Nenhum resultado corresponde aos filtros escolhidos.");
}
function row(title, records) {
  if (!records.length) return null;
  const section = document.createElement("section"); section.className = "catalogo-discovery-row";
  const heading = document.createElement("h2"); heading.textContent = title;
  const controls = document.createElement("div"); controls.className = "catalogo-row-controls";
  const previous = document.createElement("button"); previous.type = "button"; previous.textContent = "‹"; previous.setAttribute("aria-label", `Itens anteriores: ${title}`);
  const next = document.createElement("button"); next.type = "button"; next.textContent = "›"; next.setAttribute("aria-label", `Próximos itens: ${title}`);
  const track = document.createElement("div"); track.className = "catalogo-row-track"; track.setAttribute("role", "list");
  const count = Math.ceil(records.length / PAGE_SIZE); let page = 0;
  const render = () => { track.replaceChildren(...records.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE).map(createCard)); previous.disabled = page === 0; next.disabled = page + 1 >= count; };
  previous.addEventListener("click", () => { if (page > 0) { page -= 1; render(); } });
  next.addEventListener("click", () => { if (page + 1 < count) { page += 1; render(); } });
  controls.append(previous, next); section.append(heading, controls, track); render(); return section;
}
function renderDiscoveryRows() {
  const root = $("catalogo-descobertas"); root.replaceChildren();
  if ($("catalogo-termo").value || $("catalogo-filtro-a").value || $("catalogo-ong").value || $("catalogo-ods").value || Number($("catalogo-vagas").value) || locationPoint || (locationSource === "manual" && config.kind === "volunteer") || $("catalogo-data-inicio").value || $("catalogo-data-fim").value) { root.hidden = true; return; }
  root.hidden = false;
  const recent = [...sourceItems].sort((a, b) => itemDate(b) - itemDate(a));
  const rows = [row("Mais recentes", recent)];
  if (config.kind === "ongs") {
    const set = new Set(sourceItems.map(item => item.causaPrincipal).filter(Boolean));
    for (const cause of [...set].slice(0, 4)) rows.push(row(`Por causa · ${cause.replaceAll("_", " ").toLocaleLowerCase("pt-BR")}`, sourceItems.filter(item => item.causaPrincipal === cause)));
    if (locationPoint) rows.push(row("Perto de você", resultItems));
  } else if (config.kind === "volunteer") {
    for (const modality of ["PRESENCIAL", "HIBRIDO", "REMOTO"]) rows.push(row(`Por modalidade · ${modality.toLocaleLowerCase("pt-BR")}`, sourceItems.filter(item => item.vaga?.modalidadeVoluntariado === modality)));
  } else if (config.kind === "posts") {
    const set = new Set(sourceItems.map(item => item.causaPrincipalOsc).filter(Boolean));
    for (const cause of [...set].slice(0, 4)) rows.push(row(`Por causa · ${cause.replaceAll("_", " ").toLocaleLowerCase("pt-BR")}`, sourceItems.filter(item => item.causaPrincipalOsc === cause)));
  }
  rows.filter(Boolean).forEach(section => root.append(section));
}
async function resolveLocation(raw, chosenState = "") {
  let parsed = parseCatalogLocation(raw, chosenState);
  if (!parsed.ok) return parsed;
  if (parsed.type === "cep") {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 7000);
    try {
      const response = await fetch(`https://viacep.com.br/ws/${parsed.cep}/json/`, {
        headers: { Accept: "application/json" }, signal: controller.signal
      });
      if (!response.ok) return { ok: false, error: response.status === 404 ? "CEP não encontrado. Confira os números." : "Não foi possível consultar esse CEP agora." };
      const address = await response.json();
      if (address.erro || !address.localidade || !address.uf) return { ok: false, error: "CEP não encontrado. Confira os números." };
      parsed = { ...parsed, city: text(address.localidade), state: text(address.uf).toUpperCase() };
    } catch (error) {
      return { ok: false, error: error.name === "AbortError" ? "A consulta do CEP expirou. Tente novamente." : "Não foi possível consultar o CEP. Confira sua conexão." };
    } finally { clearTimeout(timeout); }
  }
  if (config.kind === "volunteer") return { ...parsed, ok: true, coordinates: null, label: formatarLocal(parsed.city, parsed.state) };
  const url = new URL("/geocoding/location", API);
  url.searchParams.set("cidade", parsed.city);
  if (parsed.state) url.searchParams.set("estado", parsed.state);
  if (parsed.cep) url.searchParams.set("cep", parsed.cep);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 9000);
  try {
    const response = await fetch(url, { headers: { Accept: "application/json" }, cache: "no-store", signal: controller.signal });
    if (!response.ok) {
      let detail = "";
      try { detail = (await response.json()).detail || ""; } catch { /* resposta sem corpo JSON */ }
      return { ok: false, error: geocodingFailureMessage(response.status, detail) };
    }
    const result = await response.json();
    const coordinates = validCatalogCoordinates(result.latitude, result.longitude);
    if (!coordinates) return { ok: false, error: "O serviço retornou coordenadas inválidas. Nenhum filtro de distância foi aplicado." };
    return { ...parsed, ok: true, coordinates, label: formatarLocal(parsed.city, parsed.state) };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "A consulta expirou. Seus resultados atuais foram mantidos." : "Não foi possível conectar ao serviço. Seus resultados atuais foram mantidos." };
  } finally { clearTimeout(timeout); }
}

function mostrarPopoverLocal(open) {
  const panel = $("catalogo-localizacao");
  const toggle = $("catalogo-local-toggle");
  const estavaAberto = !panel.hidden;
  panel.hidden = !open;
  toggle.setAttribute("aria-expanded", String(open));
  if (!open && estavaAberto && panel.contains(document.activeElement)) toggle.focus();
  if (open) $("catalogo-local").focus();
}

async function aplicarLocalizacao({ updateUrl = true, close = true, raw = $("catalogo-local").value } = {}) {
  const button = $("catalogo-local-aplicar");
  button.disabled = true;
  button.textContent = "Verificando…";
  $("catalogo-local-status").textContent = "Validando local; os resultados atuais permanecem disponíveis.";
  try {
    const resolved = await resolveLocation(raw, $("catalogo-estado").value);
    if (!resolved.ok) {
      $("catalogo-local-status").textContent = resolved.error;
      return false;
    }
    locationPoint = resolved.coordinates;
    locationCity = resolved.city;
    locationState = resolved.state;
    locationLabel = resolved.label;
    locationSource = "manual";
    $("catalogo-local").value = locationLabel;
    $("catalogo-estado").value = locationState;
    $("catalogo-local-status").textContent = locationPoint
      ? `Local aproximado verificado. Filtro de até ${$("catalogo-distancia").value} km; posição não armazenada.`
      : "Localidade aplicada por cidade. Esta categoria não tem coordenadas da atividade para calcular distância.";
    if (close) mostrarPopoverLocal(false);
    applyFilters(updateUrl);
    return true;
  } finally {
    button.disabled = false;
    button.textContent = "Aplicar localização";
  }
}

async function usarCepCadastrado() {
  const status = $("catalogo-local-status");
  status.textContent = "Consultando o CEP do seu perfil…";
  try {
    const response = await fetchAutenticado("/usuario/perfil");
    if (response.status === 401 || response.status === 403) { status.textContent = "Entre na sua conta para usar o CEP cadastrado."; return; }
    if (!response.ok) { status.textContent = "Não foi possível ler o CEP do perfil."; return; }
    const cep = text((await response.json()).cep).replace(/\D/g, "");
    if (!/^\d{8}$/.test(cep)) { status.textContent = "Seu perfil não tem CEP válido. Informe uma cidade ou CEP."; return; }
    $("catalogo-local").value = cep;
    await aplicarLocalizacao({ raw: cep });
  } catch { status.textContent = "Não foi possível consultar sua sessão. Você ainda pode informar uma cidade ou CEP."; }
}

function limparLocalizacao({ updateUrl = true } = {}) {
  locationPoint = null;
  locationCity = "";
  locationState = "";
  locationLabel = "";
  locationSource = "";
  $("catalogo-local").value = "";
  $("catalogo-estado").value = "";
  $("catalogo-local-status").textContent = "Localização removida.";
  applyFilters(updateUrl);
}

async function restaurarLocalizacaoDaUrl() {
  const local = params.get("local") || "";
  if (!local) { applyFilters(false); return; }
  if (local === "atual") {
    locationPoint = null;
    locationCity = "";
    locationState = "";
    locationLabel = "Minha localização";
    locationSource = "current-pending";
    $("catalogo-local-status").textContent = "Para proteger sua privacidade, confirme novamente sua localização para recalcular a distância.";
    applyFilters(false);
    return;
  }
  $("catalogo-local").value = local;
  $("catalogo-estado").value = params.get("estado") || "";
  $("catalogo-distancia").value = params.get("distancia") || "10";
  await aplicarLocalizacao({ updateUrl: false, close: false, raw: local });
}

function bind() {
  $("catalogo-filtros").addEventListener("submit", event => {
    event.preventDefault();
    if (!$("catalogo-localizacao").hidden && $("catalogo-local").value.trim()) void aplicarLocalizacao();
    else applyFilters();
  });
  $("catalogo-limpar").addEventListener("click", () => {
    $("catalogo-filtros").reset();
    $("catalogo-avancados").open = false;
    mostrarPopoverLocal(false);
    limparLocalizacao({ updateUrl: false });
    applyFilters();
  });
  $("catalogo-local-toggle").addEventListener("click", () => mostrarPopoverLocal($("catalogo-localizacao").hidden));
  $("catalogo-local-fechar").addEventListener("click", () => mostrarPopoverLocal(false));
  $("catalogo-local-aplicar").addEventListener("click", () => void aplicarLocalizacao());
  $("catalogo-local").addEventListener("keydown", event => {
    if (event.key === "Escape") { mostrarPopoverLocal(false); $("catalogo-local-toggle").focus(); }
    if (event.key === "Enter") { event.preventDefault(); void aplicarLocalizacao(); }
  });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !$("catalogo-localizacao").hidden) mostrarPopoverLocal(false);
  });
  $("catalogo-repetir").addEventListener("click", () => void loadBounded());
  $("catalogo-carregar-mais").addEventListener("click", () => void loadMoreBounded());
  $("catalogo-anterior").addEventListener("click", () => { resultPage -= 1; renderResultPage(); });
  $("catalogo-proximo").addEventListener("click", () => { resultPage += 1; renderResultPage(); });
  $("catalogo-distancia").addEventListener("change", () => applyFilters());
  $("catalogo-usar-localizacao").addEventListener("click", () => {
    if (!navigator.geolocation) { $("catalogo-local-status").textContent = "Este navegador não oferece localização."; return; }
    $("catalogo-local-status").textContent = "Aguardando sua autorização…";
    navigator.geolocation.getCurrentPosition(position => {
      const coordinates = validCatalogCoordinates(position.coords.latitude, position.coords.longitude);
      if (!coordinates) { $("catalogo-local-status").textContent = "Coordenadas inválidas. Resultados mantidos."; return; }
      locationPoint = coordinates;
      locationCity = "";
      locationState = "";
      locationLabel = "Minha localização";
      locationSource = "current";
      $("catalogo-local").value = "";
      $("catalogo-estado").value = "";
      $("catalogo-local-status").textContent = `Distância calculada até ${$("catalogo-distancia").value} km. Coordenadas mantidas apenas nesta página.`;
      mostrarPopoverLocal(false);
      applyFilters(true);
    }, error => {
      $("catalogo-local-status").textContent = error.code === 1 ? "Localização recusada. Resultados mantidos; tente cidade ou CEP." : "Não foi possível obter localização. Resultados mantidos.";
    }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 });
  });
  $("catalogo-remover-localizacao").addEventListener("click", () => limparLocalizacao());
  $("catalogo-usar-cep").addEventListener("click", () => void usarCepCadastrado());
  window.addEventListener("popstate", () => {
    const current = new URLSearchParams(location.search);
    $("catalogo-termo").value = current.get("q") || "";
    $("catalogo-filtro-a").value = current.get("filtro") || "";
    $("catalogo-ong").value = current.get("ong") || "";
    $("catalogo-ods").value = current.get("ods") || "";
    $("catalogo-vagas").value = current.get("vagas") || "0";
    $("catalogo-ordem").value = current.get("ordem") || "recentes";
    $("catalogo-data-inicio").value = current.get("de") || "";
    $("catalogo-data-fim").value = current.get("ate") || "";
    $("catalogo-distancia").value = current.get("distancia") || "10";
    if (current.get("local") === "atual") {
      locationPoint = null;
      locationCity = "";
      locationState = "";
      locationLabel = "Minha localização";
      locationSource = "current-pending";
      $("catalogo-local-status").textContent = "Para proteger sua privacidade, confirme novamente sua localização para recalcular a distância.";
      applyFilters(false);
    } else if (current.get("local")) {
      $("catalogo-local").value = current.get("local");
      $("catalogo-estado").value = current.get("estado") || "";
      void aplicarLocalizacao({ updateUrl: false, close: false, raw: current.get("local") });
    }
    else { limparLocalizacao({ updateUrl: false }); applyFilters(false); }
  });
}

setUpPage();
if (config.kind !== "donations") { bind(); void loadBounded(); }
