// ================================================================
// playback.js — Animation tick, timer management, stats bar
// ================================================================

import { State }               from './core/state.js';
import { closestPoint }        from './core/utils.js';
import { updateSatPositions, gsObjects } from './scene/satellites.js';
import { updateDatetimeBar }   from './ui/datetime.js';
import { refreshSatList }      from './ui/leftPanel.js';
import { refreshRightPanel }   from './ui/rightPanel.js';

let timer = null;

export function tick() {
    if (!State.times.length) return;

    updateSatPositions();
    updateDatetimeBar();

    const t        = State.times[State.idx];
    const satNames = Object.keys(State.sats);
    const detected = satNames.filter(n => {
        const c = closestPoint(State.sats[n].pts, t);
        return c.detected;
    });

    document.getElementById('statsRow').innerHTML =
        `<div class="stat-pill"><span class="stat-label">Visible</span><span class="stat-val">${detected.length}/${satNames.length}</span></div>` +
        `<div class="stat-pill"><span class="stat-label">GS</span><span class="stat-val">${gsObjects.length}</span></div>`;

    document.getElementById('tSlider').value = State.idx;
    refreshSatList();
    if (State.selectedSat) refreshRightPanel(State.selectedSat);
}

export function restartTimer() {
    clearInterval(timer);
    if (!State.playing || !State.times.length) return;
    const mult = State.speedMultiplier;
    if (mult >= 1) {
        const steps = Math.max(1, Math.round(mult));
        timer = setInterval(() => { State.set('idx', (State.idx + steps) % State.times.length); tick(); }, 16);
    } else {
        const intervalMs = Math.round(16 / mult);
        timer = setInterval(() => { State.set('idx', (State.idx + 1) % State.times.length); tick(); }, intervalMs);
    }
}

export function stopTimer() {
    clearInterval(timer);
}