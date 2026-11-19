export function possuiDadosDeModeracao(subject) {
    return Number(subject?.score) > 0
        || subject?.temporaryRestrictionActive === true
        || subject?.permanentRestrictionActive === true
        || (Array.isArray(subject?.pointsHistory) && subject.pointsHistory.length > 0)
        || (Array.isArray(subject?.appeals) && subject.appeals.length > 0)
        || (Array.isArray(subject?.reasons) && subject.reasons.length > 0);
}

export function possuiHistoricoModeracaoOng(subject) {
    return subject?.hasPointHistory === true
        || subject?.temporaryRestrictionActive === true
        || subject?.permanentRestrictionActive === true
        || (Array.isArray(subject?.pointsHistory) && subject.pointsHistory.length > 0)
        || (Array.isArray(subject?.appeals) && subject.appeals.length > 0);
}
