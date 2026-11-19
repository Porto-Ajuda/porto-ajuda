const brl = new Intl.NumberFormat("pt-BR", { style:"currency", currency:"BRL" });

export function campaignMoney(cents) {
  return brl.format(Number(cents || 0) / 100);
}

export function campaignCurrencyToCents(value) {
  let raw = String(value || "").trim().replace(/^R\$\s*/i, "").replace(/\s/g, "");
  if (!raw) return null;
  if (raw.includes(",")) raw = raw.replace(/\./g, "").replace(",", ".");
  else if (/^\d{1,3}(?:\.\d{3})+$/.test(raw)) raw = raw.replace(/\./g, "");
  if (!/^\d+(?:\.\d{1,2})?$/.test(raw)) return null;
  const [whole, decimal = ""] = raw.split(".");
  const cents = BigInt(whole) * 100n + BigInt((decimal + "00").slice(0, 2));
  return cents <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(cents) : null;
}
