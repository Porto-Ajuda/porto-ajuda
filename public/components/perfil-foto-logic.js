export function mensagemErroRemocaoFoto(status) {
    if (status === 403) return "O servidor recusou a solicitação de remoção. Atualize a página e tente novamente.";
    if (status === 404 || status === 405) return "Esta instância ainda não reconhece a remoção de foto. Atualize o serviço antes de tentar novamente.";
    if (status === 409) return "A foto foi atualizada em outra aba. Atualize o perfil antes de tentar novamente.";
    if (Number(status) >= 500) return "O servidor não confirmou a remoção. Confira o perfil e tente novamente.";
    return "Não foi possível remover a foto. Confira sua conexão e tente novamente.";
}

export function fotoRemovidaNoPerfil(perfil) {
    return Boolean(perfil && typeof perfil === "object" && !perfil.fotoPerfilUrl);
}
