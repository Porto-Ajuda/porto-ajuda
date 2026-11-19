import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado } from "./api.js";

document.addEventListener("DOMContentLoaded", async () => {
  const status = document.getElementById("campanhas-status");
  const lista = document.getElementById("campanhas-lista");
  const detalhe = document.getElementById("campanha-detalhe");
  const api = getAPI_URL().replace(/\/+$/, "");
  const brl = cents => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(cents || 0) / 100);
  const card = item => {
    const article = document.createElement("article"); article.className = "campanha-card";
    if (item.imageUrl) { const image = document.createElement("img"); image.src = item.imageUrl; image.alt = ""; image.loading = "lazy"; article.append(image); }
    const body = document.createElement("div"); body.className = "campanha-card__body";
    const ngo = document.createElement("p"); ngo.className = "campanha-card__ngo"; ngo.textContent = item.ngoName || "Organização";
    const title = document.createElement("h2"); title.textContent = item.title;
    const desc = document.createElement("p"); desc.textContent = item.description;
    const amount = document.createElement("p"); amount.textContent = `${brl(item.raisedNetCents)} arrecadados de ${brl(item.goalNetCents)} líquidos`;
    const progress = document.createElement("progress"); progress.max = 100; progress.value = Math.min(100, Math.floor(100 * Number(item.raisedNetCents || 0) / Number(item.goalNetCents || 1))); progress.setAttribute("aria-label", `${progress.value}% da meta líquida`);
    const button = document.createElement("button"); button.type = "button"; button.textContent = "Ver campanha"; button.addEventListener("click", () => showDetail(item));
    body.append(ngo,title,desc,amount,progress,button); article.append(body); return article;
  };
  function showDetail(item) {
    detalhe.hidden = false; detalhe.replaceChildren();
    const title=document.createElement("h2"); title.textContent=item.title;
    const ngo=document.createElement("p"); ngo.textContent=`${item.ngoName} · Meta líquida: ${brl(item.goalNetCents)}`;
    const raised=document.createElement("p"); raised.textContent=`${brl(item.raisedNetCents)} arrecadados`;
    const desc=document.createElement("p"); desc.textContent=item.description;
    const donate=document.createElement("form"); donate.className="campanha-doacao-form";
    const report=document.createElement("button");report.type="button";report.className="btn btn-secundario";report.textContent="Denunciar campanha";report.dataset.contentReport="true";report.dataset.reportType="CAMPANHA";report.dataset.reportId=String(item.id);
    const amountLabel=document.createElement("label"); amountLabel.textContent="Valor líquido desejado para a ONG (R$)";
    const amount=document.createElement("input"); amount.type="number";amount.min="5";amount.step="0.01";amount.inputMode="decimal";amount.required=true;amount.setAttribute("aria-label",amountLabel.textContent);
    amountLabel.append(amount);
    const methodLabel=document.createElement("label");methodLabel.textContent="Forma de pagamento";
    const method=document.createElement("select");method.innerHTML='<option value="CARD">Cartão de crédito (estimativa de pior caso)</option>';methodLabel.append(method);
    const quoteButton=document.createElement("button");quoteButton.type="button";quoteButton.textContent="Calcular total e revisar custos";
    const feedback=document.createElement("p");feedback.setAttribute("role","status");feedback.setAttribute("aria-live","polite");
    const breakdown=document.createElement("div");breakdown.className="campanha-cotacao";breakdown.hidden=true;
    const confirmLabel=document.createElement("label");const confirm=document.createElement("input");confirm.type="checkbox";confirmLabel.append(confirm,document.createTextNode(" Confirmo o valor total e a divisão dos custos acima."));confirmLabel.hidden=true;
    const pay=document.createElement("button");pay.type="button";pay.textContent="Continuar para pagamento de teste";pay.hidden=true;
    const note=document.createElement("p");note.textContent="Estimativa de teste baseada em tarifas públicas de cartão. Custos específicos da conta, Connect, saques, reembolsos e contestações não estão confirmados; a margem não é garantida. A contribuição só aparece como arrecadada após confirmação da Stripe.";
    donate.append(amountLabel,methodLabel,quoteButton,feedback,breakdown,confirmLabel,pay,note);
    donate.addEventListener("submit",event=>event.preventDefault());
    let activeQuote=null;
    quoteButton.addEventListener("click",async()=>{
      const cents=Math.round(Number(amount.value)*100);
      if(!Number.isSafeInteger(cents)||cents<500){feedback.textContent="Informe um valor líquido de pelo menos R$ 5,00.";amount.focus();return;}
      quoteButton.disabled=true;pay.hidden=true;confirmLabel.hidden=true;breakdown.hidden=true;feedback.textContent="Calculando cotação…";
      try{
        const response=await fetchAutenticado(`/donation-campaigns/${item.id}/quotes`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({netAmountCents:cents,paymentMethod:method.value})});
        let q={};try{q=await response.json();}catch{/* Authentication errors can have an empty response body. */}
        if(!response.ok)throw new Error(response.status===401||response.status===403?"Entre com uma conta de usuário para calcular a cotação.":q.detail||"Não foi possível calcular a cotação.");
        activeQuote=q;breakdown.replaceChildren();
        const rows=[["A ONG receberá (líquido)",q.netAmountCents],["Processamento estimado",q.processingFeeCents],["Parcela da plataforma",q.platformFeeCents],["Outros custos conhecidos",q.additionalKnownFeeCents],["Total a pagar",q.totalCents]];
        for(const [label,value] of rows){const p=document.createElement("p");p.textContent=`${label}: ${brl(value)}`;breakdown.append(p);}
        const expiry=document.createElement("p");expiry.textContent=`Cotação válida até ${new Date(q.expiresAt).toLocaleString("pt-BR")}.`;breakdown.append(expiry);
        const limitation=document.createElement("p");limitation.textContent=q.pricingNote;breakdown.append(limitation);
        breakdown.hidden=false;confirmLabel.hidden=false;pay.hidden=false;feedback.textContent="Revise os valores e confirme para continuar.";
      }catch(error){activeQuote=null;feedback.textContent=error.message||"Não foi possível gerar a cotação.";}
      finally{quoteButton.disabled=false;}
    });
    pay.addEventListener("click",async()=>{
      if(!activeQuote||!confirm.checked){feedback.textContent="Confirme a cotação antes de continuar.";return;}
      pay.disabled=true;feedback.textContent="Preparando checkout seguro…";
      try{
        const response=await fetchAutenticado(`/donation-campaigns/quotes/${encodeURIComponent(activeQuote.quoteId)}/checkout`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({confirmed:true})});
        let result={};try{result=await response.json();}catch{/* Authentication errors can have an empty response body. */}
        if(!response.ok)throw new Error(response.status===401||response.status===403?"Entre com uma conta de usuário para continuar.":result.detail||"O checkout não está disponível neste ambiente.");
        location.assign(result.url);
      }catch(error){feedback.textContent=error.message||"O checkout não está disponível neste ambiente.";pay.disabled=false;}
    });
    amount.addEventListener("input",()=>{activeQuote=null;pay.hidden=true;confirmLabel.hidden=true;breakdown.hidden=true;confirm.checked=false;});
    detalhe.append(title,ngo,raised,desc,donate,report); detalhe.scrollIntoView({behavior:"smooth",block:"start"});
  }
  try {
    const response=await fetch(`${api}/donation-campaigns`,{credentials:"include"});
    if(!response.ok) throw new Error("Falha ao carregar");
    const items=await response.json(); lista.replaceChildren(...items.map(card));
    status.textContent=items.length ? "Campanhas ativas" : "Ainda não há campanhas ativas.";
  } catch { status.textContent="Não foi possível carregar as campanhas. Tente novamente."; }
  finally { lista.setAttribute("aria-busy","false"); }
});
