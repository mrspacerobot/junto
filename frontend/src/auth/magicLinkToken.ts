import type { RouterHistory } from 'vue-router';

/**
 * Reads the magic-link token out of the current URL fragment and erases the
 * fragment in the same turn, before any navigation or network request can
 * observe it.
 *
 * Fragments are absent from request lines and `Referer` headers, keeping the
 * secret out of edge, access, and cross-origin resource logs. Because only the
 * browser can see the fragment, this is the single place responsible for
 * erasing it.
 *
 * The URL is rewritten with `replaceState` rather than `pushState` so the secret
 * leaves the current history entry instead of gaining one of its own that the
 * Back button could restore.
 *
 * @returns the token, or an empty string when the fragment carries none.
 */
export function takeMagicLinkToken(routerHistory: RouterHistory): string {
  const url = new URL(window.location.href);
  const token = new URLSearchParams(url.hash.replace(/^#/, '')).get('token') ?? '';

  // Do not accept old query-string links, but scrub them from history if one is opened.
  // This limits the lifetime of already-issued legacy URLs without perpetuating the
  // server-visible transport.
  url.searchParams.delete('token');
  url.hash = '';
  const sanitizedLocation = url.pathname + url.search;
  routerHistory.replace(sanitizedLocation);
  return token;
}
