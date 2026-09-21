// ================================================================
// data/propagation.js — Orbital & detection query helpers
// ================================================================

import { State }        from '../core/state.js';
import { formatDeltaT } from '../core/utils.js';

export function getOrbitalAtTime(satName, t) {
    const params = State.orbitalParams[satName];
    if (!params || !params.length) return null;
    return params.reduce((best, p) =>
        Math.abs(p.t - t) < Math.abs(best.t - t) ? p : best, params[0]);
}


export function getNextDetection(satName, type) {
    const sat = State.sats[satName];
    const times = State.times;
    const idx = State.idx;

    if (!sat) return null;
    if (!times || idx == null || idx >= times.length) return null;

    const currentTime = Number(times[idx]);
    console.log(currentTime);
    if (type === 'eo') {
        const windows = State.EObySat[satName] || [];

        const nowMs =
            Number(State.obsEpoch) +
            currentTime * 1000;
        const upcoming = windows
            .filter(w => w.start.getTime() > nowMs)
            .sort((a, b) => a.start.getTime() - b.start.getTime());

        if (!upcoming.length) return null;

        const next = upcoming[0];

        return {
            t: (next.start.getTime() - Number(State.obsEpoch)) / 1000,
            zone: next.zone
        };
    }

    if (type === 'satcom') {
        const upcoming = sat.pts
            .filter(pt =>
                Number(pt.t) > currentTime &&
                Number(pt.detected) === 1
            )
            .sort((a, b) => Number(a.t) - Number(b.t));

        if (!upcoming.length) return null;

        return {
            t: Number(upcoming[0].t),
            station: upcoming[0].station
        };
    }

    return null;
}
export function getVelocityKms(satName, closestT) {
    const sat   = State.sats[satName];
    if (!sat) return '—';
    const ptIdx = sat.pts.findIndex(p => p.t === closestT);
    if (ptIdx <= 0) return '—';

    const curr = sat.pts[ptIdx];
    const prev = sat.pts[ptIdx - 1];
    const dt   = curr.t - prev.t;
    if (dt <= 0) return '—';

    const dx = curr.x - prev.x;
    const dy = curr.y - prev.y;
    const dz = curr.z - prev.z;
    return (Math.sqrt(dx*dx + dy*dy + dz*dz) / dt / 1000).toFixed(3);
}

export function fmtNextEvent(targetT) {
    const currentT = State.times[State.idx] ?? 0;
    return formatDeltaT(currentT, targetT);
}

export function orbitType(e) {
    if (e < 0.01)  return 'Circular';
    if (e < 0.1)   return 'Near-circular';
    return 'Elliptical';
}