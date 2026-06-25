// ================================================================
// ui/datetime.js — Datetime bar + legend
// ================================================================

import { State } from '../core/state.js';

export function updateDatetimeBar() {
    const bar     = document.getElementById('datetimeBar');
    const obsEpoch = State.obsEpoch;
    if (obsEpoch === null) {
        bar.innerHTML = '<span class="no-epoch">No epoch set — load a satellite CSV to begin</span>';
        return;
    }
    if (!State.times.length) {
        bar.innerHTML = '<span class="no-epoch">Epoch set — load satellite CSV to begin</span>';
        return;
    }
    const ms       = obsEpoch + State.times[State.idx] * 1000;
    const iso      = new Date(ms).toISOString();
    const elapsedS = Math.round((ms - obsEpoch) / 1000);
    const elH = Math.floor(elapsedS / 3600);
    const elM = Math.floor((elapsedS % 3600) / 60);
    const elS = elapsedS % 60;
    bar.innerHTML =
        `<span class="epoch-label">SIM DATE</span>` +
        `<span class="epoch-val">${iso.substring(0,10)}&nbsp;&nbsp;${iso.substring(11,19)} UTC</span>` +
        `<span class="epoch-label" style="margin-left:16px;">ELAPSED</span>` +
        `<span class="epoch-val">T+${elH}h ${String(elM).padStart(2,'0')}m ${String(elS).padStart(2,'0')}s</span>`;
}

export function buildLegend() {
    document.getElementById('legend').innerHTML = [
        { color: '#2ecc71', label: 'Detected (real)' },
        { color: '#e74c3c', label: 'Not detected (real)' },
        { color: '#3498db', label: 'Detected (noisy)' },
        { color: '#ffffff', label: 'Not detected (noisy)' },
        { color: '#00ff88', label: 'GS active' },
        { color: '#ff4444', label: 'GS inactive' },
    ].map(i =>
        `<div class="legend-item"><div class="legend-dot" style="background:${i.color}"></div><span>${i.label}</span></div>`
    ).join('');
}