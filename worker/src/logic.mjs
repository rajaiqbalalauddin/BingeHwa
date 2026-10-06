// Pure decisions for the chapter worker. Kept free of network and Firebase so they can be
// tested in milliseconds, since "should we notify?" is where duplicate or missed alerts come from.

/** "12.5" -> 12.5, "Oneshot" or null -> null */
export function numberValue(n) {
  if (n === null || n === undefined) return null;
  const v = Number.parseFloat(String(n).trim());
  return Number.isFinite(v) ? v : null;
}

/**
 * Picks the newest chapter from a MangaDex feed answer (`data` array).
 * Highest chapter number wins; publish time breaks ties; chapters that
 * cannot be opened at all (no pages and no external link) are skipped.
 */
export function pickLatest(chapters) {
  const usable = (chapters ?? []).filter((c) => {
    const a = c?.attributes ?? {};
    return (a.pages ?? 0) > 0 || Boolean(a.externalUrl);
  });
  if (usable.length === 0) return null;
  const best = usable.reduce((top, c) => {
    const nt = numberValue(top.attributes.chapter) ?? -Infinity;
    const nc = numberValue(c.attributes.chapter) ?? -Infinity;
    if (nc !== nt) return nc > nt ? c : top;
    return (c.attributes.publishAt ?? '') > (top.attributes.publishAt ?? '') ? c : top;
  });
  return {
    id: best.id,
    number: best.attributes.chapter ?? 'Oneshot',
    publishAt: best.attributes.publishAt ?? '',
  };
}

/**
 * state = what we stored last time: { lastChapterId?, lastChapterNumber? }
 * Returns 'baseline' (first sighting: remember it, do not alert), 'notify' or 'none'.
 */
export function decide(latest, state) {
  if (!latest) return 'none';
  if (!state?.lastChapterId) return 'baseline';
  if (latest.id === state.lastChapterId) return 'none';
  const now = numberValue(latest.number);
  const before = numberValue(state.lastChapterNumber);
  // A different upload of a chapter we already announced, or an old chapter back-filled: stay quiet.
  if (now !== null && before !== null && now <= before) return 'none';
  return 'notify';
}

/** FCM data values must all be strings. The app builds the visible notification from these. */
export function buildMessage(seriesId, title, latest) {
  return {
    topic: `series_${seriesId}`,
    data: {
      seriesId: String(seriesId),
      chapterId: String(latest.id),
      title: String(title ?? 'New chapter').slice(0, 120),
      number: String(latest.number),
    },
    android: { priority: 'high' },
  };
}
