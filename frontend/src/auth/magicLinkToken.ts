/**
 * Magic-link tokens travel in the URL fragment. A fragment is never placed on the
 * request line and never sent in a `Referer` header, so the one-time secret stays
 * out of provider edge logs, server access logs, and any cross-origin request the
 * app makes for fonts, map tiles, or images.
 *
 * The trade-off is that only the browser can see it, which makes erasing it the
 * client's job. This is the single place that does so.
 */

/**
 * Reads the magic-link token out of the current URL fragment and erases the
 * fragment in the same turn, before any navigation or network request can
 * observe it.
 *
 * The URL is rewritten with `replaceState` rather than `pushState` so the secret
 * leaves the current history entry instead of gaining one of its own that the
 * Back button could restore.
 *
 * @returns the token, or an empty string when the fragment carries none.
 */
export function takeMagicLinkToken(): string {
  const url = new URL(window.location.href);
  const token = new URLSearchParams(url.hash.replace(/^#/, '')).get('token') ?? '';

  // Do not accept old query-string links, but scrub them from history if one is opened.
  // This limits the lifetime of already-issued legacy URLs without perpetuating the
  // server-visible transport.
  url.searchParams.delete('token');
  url.hash = '';
  const sanitizedLocation = url.pathname + url.search;
  const currentState = window.history.state;
  const sanitizedState =
    currentState && typeof currentState === 'object'
      ? { ...currentState, current: sanitizedLocation }
      : currentState;
  window.history.replaceState(sanitizedState, '', sanitizedLocation);
  return token;
}
