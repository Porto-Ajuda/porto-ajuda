import { urlImagemSegura } from "./ong-content.js";
import { campaignMoney } from "./campaign-money.js";

/** Shared public campaign card used by homepage and NGO profile showcases. */
export function createCampaignCard(item, { context = "public", onOpen = null } = {}) {
  const wrapper = document.createElement("div");
  wrapper.setAttribute("role", "listitem");
  wrapper.className = "campaign-showcase-item";

  const link = document.createElement("a");
  link.className = "showcase-card showcase-card-campaign";
  link.href = `/catalogo?categoria=doacoes&campanha=${encodeURIComponent(item.id)}`;
  link.setAttribute("aria-label", `Ver vaquinha ${item.title || "Campanha"}, de ${item.ngoName || "ONG"}`);

  const media = document.createElement("div");
  media.className = "showcase-card-media";
  if (item.imageUrl && urlImagemSegura(item.imageUrl)) {
    const image = document.createElement("img");
    image.src = item.imageUrl;
    image.alt = "";
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.addEventListener("error", () => {
      media.replaceChildren(fallback(item.title));
    }, { once: true });
    media.append(image);
  } else {
    media.append(fallback(item.title));
  }

  const info = document.createElement("div");
  info.className = "showcase-card-informacoes campaign-showcase-info";
  info.append(text("span", "showcase-card-kicker", "VAQUINHA"));
  info.append(text("strong", "showcase-card-title", item.title || "Campanha de doação"));
  info.append(text("span", "showcase-card-ngo", item.ngoName || "Organização parceira"));
  info.append(text("span", "showcase-card-detail campaign-showcase-raised", `${campaignMoney(item.raisedNetCents)} arrecadados`));
  info.append(text("span", "showcase-card-detail", `Meta líquida: ${campaignMoney(item.goalNetCents)}`));

  const goal = Math.max(1, Number(item.goalNetCents) || 1);
  const raised = Math.max(0, Number(item.raisedNetCents) || 0);
  const percent = Math.max(0, Math.min(100, Math.floor(raised * 100 / goal)));
  const progress = document.createElement("div");
  progress.className = "campaign-showcase-progress";
  progress.setAttribute("role", "progressbar");
  progress.setAttribute("aria-valuemin", "0");
  progress.setAttribute("aria-valuemax", "100");
  progress.setAttribute("aria-valuenow", String(percent));
  progress.setAttribute("aria-label", `${percent}% da meta líquida`);
  const bar = document.createElement("span");
  bar.style.width = `${percent}%`;
  progress.append(bar);
  info.append(progress);
  info.append(text("span", "campaign-showcase-action", "Ver vaquinha"));

  link.append(media, info);
  if (typeof onOpen === "function") link.addEventListener("click", event => { event.preventDefault(); onOpen(item, link); });
  wrapper.append(link);
  wrapper.dataset.context = context;
  return wrapper;
}

function text(tag, className, value) {
  const element = document.createElement(tag);
  element.className = className;
  element.textContent = value;
  return element;
}

function fallback(title) {
  const element = document.createElement("div");
  element.className = "showcase-card-preview campaign-showcase-fallback";
  const kicker = text("span", "showcase-card-kicker", "PORTO AJUDA");
  const name = text("strong", "showcase-card-preview-title", title || "Apoie uma causa");
  element.append(kicker, name);
  return element;
}
