export function fisherYatesShuffle(items, random = Math.random) {
    const shuffled = Array.isArray(items) ? [...items] : [];
    for (let index = shuffled.length - 1; index > 0; index -= 1) {
        const sample = Number(random());
        const normalized = Number.isFinite(sample) ? Math.max(0, Math.min(sample, 1 - Number.EPSILON)) : 0;
        const target = Math.floor(normalized * (index + 1));
        [shuffled[index], shuffled[target]] = [shuffled[target], shuffled[index]];
    }
    return shuffled;
}

export function uniqueById(items) {
    return [...new Map((Array.isArray(items) ? items : [])
        .filter(item => item?.id != null)
        .map(item => [String(item.id), item])).values()];
}

export async function loadCompleteDataset(fetchPage, pageSize = 24) {
    if (typeof fetchPage !== "function" || !Number.isInteger(pageSize) || pageSize < 1) {
        throw new TypeError("Parâmetros inválidos para carregar o conteúdo público.");
    }

    const validatePage = page => {
        const totalPages = Number(page?.totalPages);
        const totalElements = Number(page?.totalElements);
        if (!Array.isArray(page?.content)
            || !Number.isInteger(totalPages) || totalPages < 0
            || !Number.isInteger(totalElements) || totalElements < 0) {
            throw new TypeError("Resposta paginada inválida.");
        }
        return { totalPages, totalElements };
    };

    const first = await fetchPage(0, pageSize);
    const totals = validatePage(first);
    const records = [...first.content];
    for (let pageNumber = 1; pageNumber < totals.totalPages; pageNumber += 1) {
        const page = await fetchPage(pageNumber, pageSize);
        const currentTotals = validatePage(page);
        if (currentTotals.totalPages !== totals.totalPages || currentTotals.totalElements !== totals.totalElements) {
            throw new Error("A lista pública mudou durante o carregamento; tente novamente.");
        }
        records.push(...page.content);
    }

    const unique = uniqueById(records);
    if (unique.length !== totals.totalElements) {
        throw new Error("A lista pública mudou durante o carregamento; tente novamente.");
    }
    return unique;
}
