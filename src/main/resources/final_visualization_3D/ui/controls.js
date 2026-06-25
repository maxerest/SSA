// ================================================================
// ui/controls.js — Toolbar & file input event listeners
// ================================================================

import { State }                      from '../core/state.js';
import { sliderToSpeed, formatSpeed } from '../core/utils.js';
import { satObjects, gsObjects }      from '../scene/satellites.js';
import { tick, restartTimer, stopTimer } from '../playback.js';
import { deselectSat }                from './rightPanel.js';
import {
    loadSatCSV, loadGSCSV, loadOrbitalCSV,
    loadSatcomCSV, loadInitialPositionCSV,
} from '../loaders.js';

function readFile(input, cb) {
    const f = input.files[0];
    if (!f) return;
    const r = new FileReader();
    r.onload = ev => cb(ev.target.result);
    r.readAsText(f);
    input.value = '';
}

export function initControls(ws) {
    // ── Load dropdown ──────────────────────────────────────────
    const loadBtn  = document.getElementById('loadBtn');
    const loadMenu = document.getElementById('loadMenu');

    loadBtn.addEventListener('click', e => {
        e.stopPropagation();
        const open = loadMenu.classList.toggle('open');
        loadBtn.classList.toggle('open', open);
    });
    document.addEventListener('click', () => {
        loadMenu.classList.remove('open');
        loadBtn.classList.remove('open');
    });

    document.getElementById('mi-sat')    .addEventListener('click', () => document.getElementById('csvFile').click());
    document.getElementById('mi-gs')     .addEventListener('click', () => document.getElementById('gsFile').click());
    document.getElementById('mi-orbital').addEventListener('click', () => document.getElementById('orbitalFile').click());
    document.getElementById('mi-satcom') .addEventListener('click', () => document.getElementById('satcomFile').click());

    // ── File inputs ────────────────────────────────────────────
    document.getElementById('csvFile')    .addEventListener('change', e => readFile(e.target, loadSatCSV));
    document.getElementById('gsFile')     .addEventListener('change', e => readFile(e.target, loadGSCSV));
    document.getElementById('orbitalFile').addEventListener('change', e => readFile(e.target, loadOrbitalCSV));
    document.getElementById('satcomFile') .addEventListener('change', e => readFile(e.target, loadSatcomCSV));

    // ── Playback ───────────────────────────────────────────────
    document.getElementById('playBtn').addEventListener('click', () => {
        State.set('playing', !State.playing);
        document.getElementById('playBtn').textContent = State.playing ? '⏸ Pause' : '▶ Play';
        if (State.playing) restartTimer(); else stopTimer();
    });

    document.getElementById('tSlider').addEventListener('input', e => {
        State.set('idx', parseInt(e.target.value));
        tick();
    });

    document.getElementById('speedSlider').addEventListener('input', e => {
        const mult = sliderToSpeed(parseInt(e.target.value));
        State.set('speedMultiplier', mult);
        document.getElementById('speedLabel').textContent = formatSpeed(mult);
        restartTimer();
    });

    // ── View toggles ───────────────────────────────────────────
    document.getElementById('trailToggle').addEventListener('click', function () {
        State.set('showTrails', !State.showTrails);
        this.classList.toggle('active', State.showTrails);
        Object.values(satObjects).forEach(o => { o.trailPts.visible = State.showTrails; });
    });

    document.getElementById('coneToggle').addEventListener('click', function () {
        State.set('showCones', !State.showCones);
        this.classList.toggle('active', State.showCones);
        gsObjects.forEach(o => { o.cone.visible = State.showCones; });
    });

    // ── Backend actions ────────────────────────────────────────
    document.getElementById('propagate').addEventListener('click', () => ws.send('propagate'));
    document.getElementById('configure').addEventListener('click', () => ws.send('configure'));

    // ── Right panel close ──────────────────────────────────────
    document.getElementById('rightPanelClose').addEventListener('click', deselectSat);
}