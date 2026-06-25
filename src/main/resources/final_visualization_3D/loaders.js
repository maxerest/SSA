// ================================================================
// loaders.js — CSV → State + scene side-effects
// ================================================================

import { State }  from './core/state.js';
import { parseSatCSV, parseInitialPositionCSV, parseGSCSV, parseOrbitalCSV, parseSatcomCSV } from './data/parsers.js';
import { ecefToThreeJS } from './core/utils.js';
import { createSatObjects, removeSatObjects, satObjects, addGroundStation, clearGroundStations } from './scene/satellites.js';
import { buildLegend, updateDatetimeBar } from './ui/datetime.js';
import { buildSatList }  from './ui/leftPanel.js';
import { refreshRightPanel } from './ui/rightPanel.js';
import { tick, restartTimer } from './playback.js';

/** Set by main.js after scene is created */
let _scene = null;
export function setScene(scene) { _scene = scene; }

export function loadSatCSV(text) {
    const result = parseSatCSV(text);
    if (result.error) { console.error(result.error); return; }
    const { sats, times, epoch } = result;

    if (State.obsEpoch === null) State.set('obsEpoch', epoch);

    Object.keys(State.sats).forEach(name => removeSatObjects(_scene, name));

    State.set('sats', sats);
    State.set('times', times);
    State.set('idx', 0);

    Object.entries(sats).forEach(([name, sat]) => createSatObjects(_scene, name, sat.colorHex));

    const sl = document.getElementById('tSlider');
    sl.min = 0; sl.max = times.length - 1; sl.value = 0;

    document.getElementById('status').textContent =
        `${Object.keys(sats).length} satellite(s), ${times.length} time steps loaded.`;

    buildLegend();
    updateDatetimeBar();
    buildSatList();

    State.set('playing', true);
    document.getElementById('playBtn').textContent = '⏸ Pause';
    restartTimer();
    tick();
}

export function loadInitialPositionCSV(text) {
    const result = parseInitialPositionCSV(text);
    if (result.error) { console.error(result.error); return; }
    const { sats } = result;

    State.set('sats', sats);

    Object.entries(sats).forEach(([satName, sat]) => {
        createSatObjects(_scene, satName, sat.colorHex);
        const obj = satObjects[satName];
        if (!obj) return;
        const pt = sat.pts[0];
        const [tx, ty, tz] = ecefToThreeJS([pt.x, pt.y, pt.z]);
        obj.mesh.position.set(tx, ty, tz);
        obj.mesh.visible = true;
        obj.mesh.material.color.set(sat.colorHex);
        obj.mesh.material.emissive.set(sat.colorHex).multiplyScalar(0.25);
        obj.trailPts.visible = false;
    });

    document.getElementById('status').textContent =
        `${Object.keys(sats).length} satellite(s) placed — click Propagate to animate.`;
    buildSatList();
    updateDatetimeBar();
}

export function loadGSCSV(text) {
    clearGroundStations(_scene);
    const result = parseGSCSV(text);
    if (result.error) { console.error(result.error); return; }
    result.stations.forEach(s => addGroundStation(_scene, s.name, s.lat, s.lon, s.alt, s.activated));
    const cur = document.getElementById('status').textContent;
    document.getElementById('status').textContent = cur + ` | ${result.stations.length} GS loaded.`;
}

export function loadOrbitalCSV(text) {
    const result = parseOrbitalCSV(text);
    if (result.error) { console.error(result.error); return; }
    State.set('orbitalParams', result.orbitalParams);
    console.log('[3D] Orbital params loaded for:', Object.keys(result.orbitalParams).join(', '));
    buildSatList();
    if (State.selectedSat) refreshRightPanel(State.selectedSat);
}

export function loadSatcomCSV(text) {
    const result = parseSatcomCSV(text);
    if (result.error) { console.error(result.error); return; }
    State.set('satcomLinks', result.links);
    console.log('[3D] Satcom links loaded:', result.links.length);
    if (State.selectedSat) refreshRightPanel(State.selectedSat);
}