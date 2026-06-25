// ================================================================
// core/utils.js — Pure, stateless helpers
// ================================================================

import { EARTH_R } from './config.js';

export function sliderToSpeed(v) {
    if (v === 0) return 1;
    return v > 0 ? Math.pow(2, v) : 1 / Math.pow(2, -v);
}

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

export function ecefToThreeJS([x, y, z]) {
    return [y, z, x];
}

export function getSatColor(sat, pt) {
    if (sat.isNoisy) return pt.detected ? '#3498db' : '#ffffff';
    return pt.detected ? '#2ecc71' : '#e74c3c';
}

export function closestPoint(pts, t) {
    return pts.reduce((best, p) =>
        Math.abs(p.t - t) < Math.abs(best.t - t) ? p : best, pts[0]);
}

export function formatDeltaT(currentT, targetT) {
    if (targetT === null || targetT === undefined) return '—';
    const d = targetT - currentT;
    if (d <= 0) return 'now';
    const h = Math.floor(d / 3600);
    const m = Math.floor((d % 3600) / 60);
    const s = Math.floor(d % 60);
    if (h > 0) return `T+${h}h${String(m).padStart(2, '00')}m`;
    if (m > 0) return `T+${m}m${String(s).padStart(2, '00')}s`;
    return `T+${s}s`;
}

export function formatSpeed(mult) {
    return mult < 1 ? `${mult.toFixed(1)}×` : `${Math.round(mult)}×`;
}

export function safeParse(text) {
    try { return JSON.parse(text); } catch { return null; }
}