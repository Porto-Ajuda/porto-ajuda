export function profileMembershipExcludesNgo(memberships, ngoId) {
  const targetId = Number(ngoId);
  return Array.isArray(memberships) && memberships.some(item => Number(item?.id) === targetId
    && ["CRIADOR", "ADMINISTRADOR", "EDITOR", "MEMBRO"].includes(String(item?.cargo || "").toUpperCase()));
}

export function eligibleVisitTarget(ngo) {
  return Boolean(ngo && Number.isSafeInteger(Number(ngo.id)) && Number(ngo.id) > 0
    && ngo.publicEligible === true && ngo.status === "ATIVA" && ngo.arquivada !== true);
}

export function alreadyLearnedVisit(visits, ngoId, localDay) {
  return Array.isArray(visits) && visits.some(item => Number(item?.ngoId) === Number(ngoId) && item?.day === localDay);
}
