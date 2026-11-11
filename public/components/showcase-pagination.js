export const HOME_SHOWCASE_PAGE_SIZE = 6;

/** Independent, server-paginated state for one homepage showcase. */
export function createShowcasePager({ fetchPage, onItems = () => {}, onState = () => {}, pageSize = HOME_SHOWCASE_PAGE_SIZE }) {
    if (typeof fetchPage !== "function") {
        throw new TypeError("fetchPage must be a function");
    }

    const size = Math.max(1, Math.min(HOME_SHOWCASE_PAGE_SIZE, Number(pageSize) || HOME_SHOWCASE_PAGE_SIZE));
    let page = 0;
    let totalPages = 0;
    let loading = false;
    let status = "idle";
    let items = [];
    let requestedPage = 0;

    function snapshot() {
        return {
            page,
            pageSize: size,
            totalPages,
            loading,
            status,
            items: [...items],
            canPrevious: !loading && page > 0,
            canNext: !loading && page + 1 < totalPages
        };
    }

    function publishState() {
        onState(snapshot());
    }

    async function load(nextPage = 0) {
        if (loading || !Number.isInteger(nextPage) || nextPage < 0) return false;
        if (totalPages > 0 && nextPage >= totalPages) return false;

        requestedPage = nextPage;
        loading = true;
        status = "loading";
        publishState();

        try {
            const response = await fetchPage(nextPage, size);
            if (!response || !Array.isArray(response.content)
                    || !Number.isInteger(response.totalPages) || response.totalPages < 0) {
                throw new TypeError("Invalid paginated showcase response");
            }

            page = Number.isInteger(response.number) ? response.number : nextPage;
            totalPages = response.totalPages;
            items = response.content.slice(0, size);
            status = items.length === 0 ? "empty" : "ready";
            onItems([...items], snapshot());
            return true;
        } catch {
            status = "error";
            return false;
        } finally {
            loading = false;
            publishState();
        }
    }

    return Object.freeze({
        load: () => load(0),
        previous: () => load(page - 1),
        next: () => page + 1 < totalPages ? load(page + 1) : Promise.resolve(false),
        retry: () => load(requestedPage),
        getState: snapshot
    });
}
