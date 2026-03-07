import { useLocalStorage } from './use-local-storage';

const MAX_RECENT_SEARCHES = 10;

export function useRecentSearches() {
  const [recentSearches, setRecentSearches] = useLocalStorage<string[]>('recent-searches', []);

  const addSearch = (query: string) => {
    const trimmed = query.trim();
    if (!trimmed) return;

    setRecentSearches((prev) => {
      const filtered = prev.filter((s) => s !== trimmed);
      return [trimmed, ...filtered].slice(0, MAX_RECENT_SEARCHES);
    });
  };

  const removeSearch = (query: string) => {
    setRecentSearches((prev) => prev.filter((s) => s !== query));
  };

  const clearAll = () => {
    setRecentSearches([]);
  };

  return { recentSearches, addSearch, removeSearch, clearAll };
}
