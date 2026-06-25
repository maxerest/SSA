// ================================================================
// ui/leftPanel.js — Fleet overview (left panel satellite cards)
// ================================================================

import { EARTH_R }              from '../core/config.js';
import { State }                from '../core/state.js';
import { closestPoint, formatDeltaT } from '../core/utils.js';
import { getOrbitalAtTime, getNextDetection } from '../data/propagation.js';

/** Callback set by main.js so the panel can trigger selection */
let _onSelect = () => {};
export function setSelectCallback(fn) { _onSelect = fn; }

export function buildSatList() {
    const list     = document.getElementById('satList');
    const satNames = Object.keys(State.sats);
    if (!satNames.length) {
        list.innerHTML = '<div style="padding:16px 12px;font-size:11px;color:var(--text-dim);font-style:italic;">No satellites loaded</div>';
        return;
    }
    list.innerHTML = satNames.map(name => buildSatCardHTML(name)).join('');
    satNames.forEach(name => {
        document.getElementById(`satcard-${CSS.escape(name)}`)
            ?.addEventListener('click', () => _onSelect(name));
    });
}

export function refreshSatList() {
    Object.keys(State.sats).forEach(name => {
        const el = document.getElementById(`satcard-${name}`);
        if (!el) return;
        el.outerHTML = buildSatCardHTML(name);
        document.getElementById(`satcard-${name}`)
            ?.addEventListener('click', () => _onSelect(name));
    });
}

function buildSatCardHTML(satName) {
    const sat = State.sats[satName];
    if (!sat) return '';

    const t       = State.times[State.idx] || 0;
    const closest = closestPoint(sat.pts, t);
    const r       = Math.sqrt(closest.x**2 + closest.y**2 + closest.z**2);
    const altKm   = ((r - EARTH_R) / 1000).toFixed(1);
    const orb     = getOrbitalAtTime(satName, t);
    const smaKm   = orb ? (orb.a / 1000).toFixed(1) : '—';

    const nextEO     = getNextDetection(satName, 'eo');
    const nextSatcom = getNextDetection(satName, 'satcom');
    const currentT   = t;

    const detected  = closest.detected === 1;
    const cardClass = `sat-card ${detected ? 'detected' : 'not-detected'} ${State.selectedSat === satName ? 'selected' : ''}`;

    return `<div class="${cardClass}" id="satcard-${satName}">
        <div class="sat-card-name">
            <div class="sat-dot" style="background:${sat.colorHex}"></div>
            ${satName}
        </div>
        <div class="sat-card-row"><span>Alt</span><span>${altKm} km</span></div>
        <div class="sat-card-row"><span>SMA</span><span>${smaKm} km</span></div>
        <div class="sat-card-next">
            <div><span class="next-label">Next EO </span>
                 <span class="next-val ${nextEO ? '' : 'none'}">${nextEO ? formatDeltaT(currentT, nextEO.t) : 'none'}</span></div>
            <div><span class="next-label">Next DL </span>
                 <span class="next-val ${nextSatcom ? '' : 'none'}">${nextSatcom
        ? `${formatDeltaT(currentT, nextSatcom.t)}${nextSatcom.station ? ' · ' + nextSatcom.station : ''}`
        : 'none'}</span></div>
        </div>
    </div>`;
}