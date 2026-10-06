// Minimal MangaDex client for the worker. Polite on purpose: one request at a time.

const BASE = 'https://api.mangadex.org';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function fetchFeed(seriesId, lang = 'en', fetchImpl = fetch) {
  const url = new URL(`${BASE}/manga/${seriesId}/feed`);
  url.searchParams.append('translatedLanguage[]', lang);
  url.searchParams.append('order[chapter]', 'desc');
  url.searchParams.append('limit', '10');
  url.searchParams.append('contentRating[]', 'safe');
  url.searchParams.append('contentRating[]', 'suggestive');

  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await fetchImpl(url, { headers: { 'User-Agent': 'BingeHwa-worker/0.1' } });
    if (res.status === 429) {
      const wait = Number(res.headers.get('retry-after')) || 2;
      await sleep(Math.min(wait, 10) * 1000);
      continue;
    }
    if (!res.ok) throw new Error(`MangaDex answered ${res.status} for ${seriesId}`);
    const body = await res.json();
    return body.data ?? [];
  }
  throw new Error(`MangaDex kept rate-limiting ${seriesId}`);
}

export { sleep };
