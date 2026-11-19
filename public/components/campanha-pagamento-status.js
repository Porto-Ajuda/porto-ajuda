import { fetchAutenticado } from "./api.js";
const status=document.getElementById("campaign-payment-status");
const net=document.getElementById("campaign-payment-net");
const shortfall=document.getElementById("campaign-payment-shortfall");
const params=new URLSearchParams(location.search);
const session=params.get("session_id");
if(params.get("cancelled")){status.textContent="Checkout cancelado. Nenhum pagamento foi marcado como concluído.";}
else if(!session){status.textContent="Não recebemos uma sessão de checkout para consultar.";}
else {
  const brl=value=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(value||0)/100);
  let attempts=0;
  async function poll(){
    try{
      const response=await fetchAutenticado(`/donation-campaigns/payments/session/${encodeURIComponent(session)}`);
      const result=await response.json();if(!response.ok)throw new Error(result.detail||"Não foi possível consultar o status.");
      status.textContent=`${result.message} Campanha: ${result.campaignTitle}.`;
      net.textContent=`Valor líquido prometido à ONG: ${brl(result.ngoNetCents)}. Arrecadação líquida contabilizada: ${brl(result.progressNetCents)}.`;
      if(Number(result.feeShortfallCents)>0)shortfall.textContent=`A conciliação detectou custos acima da estimativa em ${brl(result.feeShortfallCents)}. A equipe financeira precisa revisar este registro.`;
      if(result.status==="PENDING"&&attempts++<20)setTimeout(poll,3000);
    }catch(error){status.textContent=error.message||"A confirmação ainda não está disponível. Volte ao seu histórico para consultar mais tarde.";}
  }
  void poll();
}
