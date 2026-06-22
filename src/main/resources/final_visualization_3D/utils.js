// ================================================================
// core/utils.js — Pure, stateless helpers
// ================================================================

import { EARTH_R } from './config.js';

/**
 * Convert slider integer [-6..6] → speed multiplier
 * 0 → 1×, +1 → 2×, -1 → 0.5×, etc.
 * @param {number} v
 * @returns {number}
 */
export function sliderToSpeed(v) {
    if (v === 0) return 1;
    return v > 0 ? Math.pow(2, v) : 1 / Math.pow(2, -v);
}

/**
 * Geodetic lat/lon/alt → ECEF metres [x, y, z]
 * @param {number} lat  degrees
 * @param {number} lon  degrees
 * @param {number} altM metres above surface
 * @returns {[number, number, number]}
 */
export function latLonToECEF(lat, lon, altM = 0) {
    const phi    = lat * Math.PI / 180;
    const lambda = lon * Math.PI / 180;
    const r      = EARTH_R + altM;
    return [
        r * Math.cos(phi) * Math.cos(lambda),
        r * Math.cos(phi) * Math.sin(lambda),
        r * Math.sin(phi),
    ];
}

/**
 * Remap ECEF [x,y,z] to Three.js world coords [y,z,x].
 * Three.js uses Y-up; our ECEF uses Z-up.
 * @param {[number,number,number]} ecef
 * @returns {[number,number,number]}
 */
export function ecefToThreeJS([x, y, z]) {
    return [y, z, x];
}

/**
 * Determine a satellite's display colour based on type & detection state.
 * @param {{ isNoisy: boolean }} sat
 * @param {{ detected: number }} pt
 * @returns {string} hex colour
 */
export function getSatColor(sat, pt) {
    if (sat.isNoisy) return pt.detected ? '#3498db' : '#ffffff';
    return pt.detected ? '#2ecc71' : '#e74c3c';
}

/**
 * Find the data point in `pts` closest to simulation time `t`.
 * @param {{ t: number }[]} pts
 * @param {number} t
 * @returns {{ t: number }}
 */
export function closestPoint(pts, t) {
    return pts.reduce((best, p) =>
        Math.abs(p.t - t) < Math.abs(best.t - t) ? p : best, pts[0]);
}

/**
 * Format a delta-time relative to current sim time.
 * @param {number} currentT  current sim time (s)
 * @param {number|null} targetT  future sim time (s)
 * @returns {string}
 */
export function formatDeltaT(currentT, targetT) {
    if (targetT === null || targetT === undefined) return '—';
    const d = targetT - currentT;
    if (d <= 0) return 'now';
    const h = Math.floor(d / 3600);
    const m = Math.floor((d % 3600) / 60);
    const s = Math.floor(d % 60);
    if (h > 0) return `T+${h}h${String(m).padStart(2, '0')}m`;
    if (m > 0) return `T+${m}m${String(s).padStart(2, '0')}s`;
    return `T+${s}s`;
}

/**
 * Format speed multiplier as a display string.
 * @param {number} mult
 * @returns {string}
 */
export function formatSpeed(mult) {
    return mult < 1 ? `${mult.toFixed(1)}×` : `${Math.round(mult)}×`;
}

/**
 * Safe wrapper around JSON.parse — returns null on failure.
 * @param {string} text
 * @returns {*}
 */
export function safeParse(text) {
    try { return JSON.parse(text); } catch { return null; }
}