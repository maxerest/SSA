// ================================================================
// main.js — Entry point: bootstraps scene, UI, comms
// ================================================================


import { EARTH_R }          from './core/config.js';
import { State }            from './core/state.js';
import { buildLights, buildStars, buildEarth } from './scene/earth.js';
import { buildCamera }      from './scene/camera.js';
import { satMeshMap }       from './scene/satellites.js';
import {  updateDatetimeBar } from './ui/datetime.js';
import { buildSatList, setSelectCallback } from './ui/leftPanel.js';
import { selectSat }        from './ui/rightPanel.js';
import { initControls }     from './ui/controls.js';
import { setScene }         from './loaders.js';
import { loadSatCSV, loadGSCSV, loadOrbitalCSV, loadSatcomCSV, loadInitialPositionCSV } from './loaders.js';
import { initWebSocket }    from './comms/websocket.js';
import { tick }             from './playback.js';

// ── Renderer ──────────────────────────────────────────────────────
const canvas   = document.getElementById('glCanvas');
const wrap     = document.getElementById('canvasWrap');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
renderer.setPixelRatio(window.devicePixelRatio);
renderer.setClearColor(0x07090f, 1);

// ── Scene ─────────────────────────────────────────────────────────
const scene = new THREE.Scene();
setScene(scene);

buildLights(scene);
buildStars(scene);
buildEarth(scene);

// ── Camera + orbit controls ───────────────────────────────────────
const { camera, isDraggingRef } = buildCamera(canvas);

// ── Raycaster (sat click-to-select) ──────────────────────────────
const raycaster = new THREE.Raycaster();
const mouseNDC  = new THREE.Vector2();

canvas.addEventListener('click', e => {
    if (isDraggingRef()) return;
    const rect = canvas.getBoundingClientRect();
    mouseNDC.x =  ((e.clientX - rect.left) / rect.width)  * 2 - 1;
    mouseNDC.y = -((e.clientY - rect.top)  / rect.height) * 2 + 1;
    raycaster.setFromCamera(mouseNDC, camera);
    const meshes = [...satMeshMap.keys()]
        .map(id => scene.getObjectByProperty('uuid', id))
        .filter(Boolean);
    const hits = raycaster.intersectObjects(meshes);
    if (hits.length) {
        const name = satMeshMap.get(hits[0].object.uuid);
        if (name) selectSat(name);
    }
});

// ── Resize ────────────────────────────────────────────────────────
function resizeIfNeeded() {
    const w = wrap.clientWidth, h = wrap.clientHeight;
    if (canvas.width !== w || canvas.height !== h) {
        renderer.setSize(w, h, false);
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
    }
}

// ── Render loop ───────────────────────────────────────────────────
(function renderLoop() {
    requestAnimationFrame(renderLoop);
    resizeIfNeeded();
    renderer.render(scene, camera);
})();

// ── WebSocket ─────────────────────────────────────────────────────
const ws = initWebSocket();

// ── UI controls ───────────────────────────────────────────────────
setSelectCallback(selectSat);
initControls(ws);

// ── Bridge (called by WebView / Java host) ────────────────────────
window.receiveCSVContent   = text => loadSatCSV(text);
window.receiveGSCSVContent = text => loadGSCSV(text);
window.receiveOrbitalCSV   = text => loadOrbitalCSV(text);
window.receiveSatcomCSV    = text => loadSatcomCSV(text);
window.setSimulationEpoch  = iso  => {
    State.set('obsEpoch', new Date(iso).getTime());
    updateDatetimeBar();
};

// ── Init ──────────────────────────────────────────────────────────
updateDatetimeBar();
buildSatList();
console.log('[3D Visualizer] Ready.');
window.visualizerReady = true;