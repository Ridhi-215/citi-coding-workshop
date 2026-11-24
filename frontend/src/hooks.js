import { useCallback, useEffect, useState } from 'react';

/** Returns value after it stopped changing for `delay` ms (for search boxes). */
export function useDebounced(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

/**
 * Loads `fetcher(params)` whenever params change (compared as JSON) or `reload()` is called.
 * Keeps the previous data visible while a new request runs; `loading` is derived, not stored.
 */
export function useQuery(fetcher, params) {
  const key = JSON.stringify(params);
  const [version, setVersion] = useState(0);
  const [result, setResult] = useState({ key: null, data: null, error: null });
  const requestKey = `${key}#${version}`;

  useEffect(() => {
    let active = true;
    const done = (data, error) => active && setResult({ key: `${key}#${version}`, data, error });
    fetcher(JSON.parse(key)).then((data) => done(data, null), (error) => done(undefined, error));
    return () => {
      active = false;
    };
  }, [fetcher, key, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  const mutate = useCallback((update) => setResult((r) => ({ ...r, data: update(r.data) })), []);
  const loading = result.key !== requestKey;
  return {
    data: result.data,
    error: loading ? null : result.error,
    loading,
    reload,
    mutate,
  };
}
