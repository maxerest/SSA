/* ================================================================
 * utils.js  –  Pure helpers (no DOM, no state)
 * ================================================================ */

/**
 * Convert speed-slider integer value → real multiplier.
 * Uses a log10 scale: 0 → 1×, 10 → 10×, -10 → 0.1×
 */
function sliderToSpeed(v) {
    return Math.pow(10, v / 10);
}

/**
 * Format a total-seconds count as HH:MM:SS.
 * Accepts an optional format string with tokens: hh h mm m ss s
 */
function formatSecondsAsTime(secs, format) {
    let hr  = Math.floor(secs / 3600);
    let min = Math.floor((secs - hr * 3600) / 60);
    let sec = Math.floor(secs - hr * 3600 - min * 60);
    const pad = n => String(n).padStart(2, '0');
    if (format != null) {
        return format
            .replace('hh', pad(hr))
            .replace('h',  hr + '')
            .replace('mm', pad(min))
            .replace('m',  min + '')
            .replace('ss', pad(sec))
            .replace('s',  sec + '');
    }
    return `${pad(hr)}:${pad(min)}:${pad(sec)}`;
}

/**
 * Format a Unix-ms timestamp as "YYYY-MM-DD HH:MM:SS" (UTC).
 */
function formatMs(ms) {
    if (isNaN(ms)) return '—';
    return new Date(ms).toISOString().replace('T', ' ').substring(0, 19);
}

/**
 * Convert a millisecond duration to a human-readable string (e.g. "2h 05m").
 */
function msToDuration(ms) {
    const s = Math.round(ms / 1000);
    if (s < 60)   return s + 's';
    if (s < 3600) return Math.floor(s / 60) + 'm ' + (s % 60) + 's';
    return Math.floor(s / 3600) + 'h ' + Math.floor((s % 3600) / 60) + 'm';
}

/**
 * Convert ECI Cartesian (x, y, z) → [longitude°, latitude°].
 */
function xyz2ll(x, y, z) {
    const r = Math.sqrt(x * x + y * y + z * z);
    return [
        Math.atan2(y, x) * 180 / Math.PI,
        Math.asin(z / r) * 180 / Math.PI
    ];
}

/**
 * Parse an ISO-8601 string to Unix-ms, tolerating extra sub-second digits.
 */
function isoToMs(isoStr) {
    if (!isoStr) return NaN;
    return Date.parse(isoStr.replace(/(\.\d{1,3})\d*/, '$1'));
}

/**
 * From an array of {t, …} point objects, return the one closest to time t.
 */
function getBest(pts, t) {
    let best = pts[0], bd = Infinity;
    for (const p of pts) {
        const d = Math.abs(p.t - t);
        if (d < bd) { bd = d; best = p; }
    }
    return best;
}