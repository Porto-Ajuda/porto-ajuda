const stateNames = new Map(Object.entries({
  acre: "AC", alagoas: "AL", amapá: "AP", amazonas: "AM", bahia: "BA", ceará: "CE",
  "distrito federal": "DF", "espírito santo": "ES", goiás: "GO", maranhão: "MA",
  "mato grosso": "MT", "mato grosso do sul": "MS", "minas gerais": "MG", pará: "PA",
  paraíba: "PB", paraná: "PR", pernambuco: "PE", piauí: "PI", "rio de janeiro": "RJ",
  "rio grande do norte": "RN", "rio grande do sul": "RS", rondônia: "RO", roraima: "RR",
  "santa catarina": "SC", "são paulo": "SP", sergipe: "SE", tocantins: "TO"
}));
const states = new Set(["AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"]);

export function parseCatalogLocation(input, selectedState = "") {
  if (typeof input !== "string") return { ok: false, error: "Informe uma cidade ou CEP." };
  if (input.length > 100 || /[\u0000-\u001f\u007f]/.test(input)) return { ok: false, error: "Informe uma cidade ou CEP válido." };
  let value = input.trim().replace(/\s+/g, " ");
  if (!value) return { ok: false, error: "Informe uma cidade ou CEP válido." };
  const postal = value.replace(/[\s-]/g, "");
  if (/^\d{8}$/.test(postal)) return { ok: true, type: "cep", cep: postal, city: "", state: "" };
  if (/^[\d\s-]+$/.test(value)) return { ok: false, error: "CEP inválido. Informe oito dígitos ou uma cidade." };

  value = value.replace(/,/g, " ").replace(/\s+/g, " ").trim();
  const tokens = value.split(" ");
  let state = String(selectedState || "").trim().toUpperCase();
  const lastToken = tokens.at(-1) || "";
  if (/^[A-Za-z]{2}$/.test(lastToken) && states.has(lastToken.toUpperCase())) {
    if (state && state !== lastToken.toUpperCase()) return { ok: false, error: "A sigla no texto não corresponde ao estado selecionado." };
    state = lastToken.toUpperCase();
    tokens.pop();
  } else {
    const normalized = value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR");
    for (const [name, uf] of stateNames) {
      const plainName = name.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
      const cityPrefix = value.slice(0, value.length - name.length).trim();
      if (cityPrefix && normalized.endsWith(` ${plainName}`)) {
        if (state && state !== uf) return { ok: false, error: "O estado no texto não corresponde ao estado selecionado." };
        state = uf;
        value = cityPrefix;
        tokens.splice(0, tokens.length, ...value.split(" "));
        break;
      }
    }
  }
  if (state && !states.has(state)) return { ok: false, error: "Selecione uma sigla de estado válida." };
  const city = tokens.join(" ").trim();
  if (!city) return { ok: false, error: "Informe o nome da cidade." };
  return { ok: true, type: "city", city, state };
}

export function validCatalogCoordinates(latitude, longitude) {
  const lat = Number(latitude), lon = Number(longitude);
  return Number.isFinite(lat) && lat >= -90 && lat <= 90
    && Number.isFinite(lon) && lon >= -180 && lon <= 180
    ? { latitude: lat, longitude: lon }
    : null;
}

export function distanceKm(from, to) {
  const radians = degrees => degrees * Math.PI / 180;
  const dLat = radians(to.latitude - from.latitude);
  const dLon = radians(to.longitude - from.longitude);
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(radians(from.latitude)) * Math.cos(radians(to.latitude)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function geocodingFailureMessage(status, detail = "") {
  if (status === 400) return "A localização não está completa ou contém um CEP inválido. Corrija e tente novamente.";
  if (status === 401 || status === 403) return "O serviço de localização foi configurado como privado. Nenhum filtro de distância foi aplicado.";
  if (status === 404) return "A rota de localização não existe neste backend. Ele pode estar rodando uma versão antiga; atualize-o e tente novamente.";
  if (status === 409) return detail || "Há mais de uma cidade com esse nome. Informe também o estado.";
  if (status === 422) return detail || "A cidade ou o CEP não foi localizado. Confira os dados.";
  if (status === 429) return "O serviço de localização está ocupado. Aguarde um pouco e tente novamente.";
  if (status >= 500) return "O serviço de localização está indisponível. Seus resultados atuais foram mantidos.";
  return "Não foi possível validar a localização. Seus resultados atuais foram mantidos.";
}
