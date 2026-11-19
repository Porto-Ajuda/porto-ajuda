export function adminAuthFailureMessage(status, code) {
    const messages = {
        INVALID_CODE: 'O c\u00f3digo est\u00e1 incorreto. Confira e tente novamente.',
        EXPIRED_CODE: 'Este c\u00f3digo expirou. Inicie um novo login para receber outro.',
        CHALLENGE_INVALIDATED: 'Esta tentativa foi invalidada. Inicie um novo login.',
        CHALLENGE_REPLACED: 'Esta tentativa foi substitu\u00edda. Use o c\u00f3digo mais recente.',
        CODE_ALREADY_USED: 'Este c\u00f3digo j\u00e1 foi utilizado. Inicie um novo login.',
        TOO_MANY_ATTEMPTS: 'O limite de tentativas foi atingido. Aguarde antes de iniciar novamente.',
        DELIVERY_FAILED: 'N\u00e3o foi poss\u00edvel entregar o c\u00f3digo. Inicie um novo login.',
        INVALID_CHALLENGE: 'Esta tentativa n\u00e3o \u00e9 v\u00e1lida. Inicie um novo login.'
    };
    if (messages[code]) return messages[code];
    if (status === 410) return messages.EXPIRED_CODE;
    if (status === 409) return messages.CHALLENGE_REPLACED;
    if (status === 503) return messages.DELIVERY_FAILED;
    if (status === 401) return 'O c\u00f3digo est\u00e1 incorreto ou esta tentativa n\u00e3o \u00e9 mais v\u00e1lida.';
    if (status === 403) return 'A solicita\u00e7\u00e3o foi recusada. Reabra o acesso e tente novamente.';
    if (status === 429) return messages.TOO_MANY_ATTEMPTS;
    return 'N\u00e3o foi poss\u00edvel concluir a solicita\u00e7\u00e3o. Tente novamente em instantes.';
}

export function adminAuthNextFactor(response) {
    if (response?.nextFactor === 'EMAIL' || response?.nextFactor === 'SMS'
            || response?.nextFactor === 'DELIVERY') {
        return response.nextFactor;
    }
    if (response?.codigo === 'DELIVERY_PENDING') return 'DELIVERY';
    return 'UNKNOWN';
}
