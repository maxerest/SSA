// ================================================================
// ui/maneuverModal.js — Maneuver creation modal (mini orbit preview)
// ================================================================

import { State }          from '../core/state.js';
import { EARTH_R }        from '../core/config.js';
import { ecefToThreeJS }  from '../core/utils.js';

let ws = null;
let scene, camera, renderer, satMesh, orbitLine;
let animId = null;
let currentSatName = null;
const EARTH_MU = 3.986004418e14; // m³/s²
export function setupManeuverModal(wsInstance) {
    ws = wsInstance;

    document.getElementById('maneuverBtn').addEventListener('click', () => {
        if (State.selectedSat) openManeuverModal(State.selectedSat);
    });
    document.getElementById('maneuverModalClose').addEventListener('click', closeManeuverModal);
    document.getElementById('maneuverCancelBtn').addEventListener('click', closeManeuverModal);
    document.getElementById('maneuverConfirmBtn').addEventListener('click', confirmManeuver);
    document.getElementById('maneuverTimeSlider').addEventListener('input', onSliderInput);
}

function buildMiniScene(canvas) {
    scene  = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(45, canvas.clientWidth / canvas.clientHeight, EARTH_R * 0.01, EARTH_R * 25);
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    renderer.setPixelRatio(window.devicePixelRatio);

    const light = new THREE.DirectionalLight(0xffffff, 1.2);
    light.position.set(1, 1, 1);
    scene.add(light);
    scene.add(new THREE.AmbientLight(0x334455, 0.8));

    const earthGeo = new THREE.SphereGeometry(EARTH_R, 64, 64);
    const texLoader = new THREE.TextureLoader();
    const earthMat = new THREE.MeshPhongMaterial({
        color: 0x1a6fa0,
        emissive: 0x0a1a2e,
        specular: 0x2244aa,
        shininess: 12,
    });
    texLoader.load('/scene/earth-blue-marble.jpg', tex => {
        earthMat.map = tex;
        earthMat.needsUpdate = true;
        window.GlobeInitialized = true;
    }, undefined, err => {
        console.error('[earth] Texture load failed:', err);
        window.GlobeInitialized = true;
    });

    const earthMesh = new THREE.Mesh(earthGeo, earthMat);
    earthMesh.rotation.y = -Math.PI/2;
    scene.add(earthMesh);

    satMesh = new THREE.Mesh(
        new THREE.SphereGeometry(EARTH_R * 0.02, 12, 12),
        new THREE.MeshBasicMaterial({ color: 0xffcc00 })
    );
    scene.add(satMesh);
}

function buildOrbitLine(sat) {
    if (orbitLine) scene.remove(orbitLine);
    const pts = sat.pts.map(p => {
        const [x, y, z] = ecefToThreeJS([p.x, p.y, p.z]);
        return new THREE.Vector3(x, y, z);
    });
    const geo = new THREE.BufferGeometry().setFromPoints(pts);
    orbitLine = new THREE.LineLoop(geo, new THREE.LineBasicMaterial({ color: 0x4fc3f7 }));
    scene.add(orbitLine);
}

function resizeCanvas(canvas) {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.position.set(0, EARTH_R * 4, EARTH_R * 6);
    camera.lookAt(0, 0, 0);
    camera.updateProjectionMatrix();
}

function updateSatMeshPosition(sat, idx) {
    const p = sat.pts[idx];
    if (!p) return;
    const [x, y, z] = ecefToThreeJS([p.x, p.y, p.z]);
    satMesh.position.set(x, y, z);
    document.getElementById('maneuverTimeLabel').textContent = `T = ${p.t.toFixed(0)}s`;
}

function openManeuverModal(satName) {
    currentSatName = satName;
    const sat = State.sats[satName];
    if (!sat) return;

    document.getElementById('maneuverModalTitle').textContent = `Maneuver — ${satName}`;
    document.getElementById('maneuverModal').style.display = 'flex';

    const canvas = document.getElementById('maneuverCanvas');
    if (!scene) buildMiniScene(canvas);
    buildOrbitLine(sat);

    const slider = document.getElementById('maneuverTimeSlider');
    slider.min = 0;
    slider.max = sat.pts.length - 1;
    slider.value = 0;
    updateSatMeshPosition(sat, 0);

    resizeCanvas(canvas);
    if (!animId) animate();
}

function onSliderInput(e) {
    const sat = State.sats[currentSatName];
    if (!sat) return;
    updateSatMeshPosition(sat, parseInt(e.target.value));
}

function animate() {
    animId = requestAnimationFrame(animate);
    if (renderer) renderer.render(scene, camera);
}

function closeManeuverModal() {
    document.getElementById('maneuverModal').style.display = 'none';
}

function confirmManeuver() {
    const sat = State.sats[currentSatName];
    if (!sat || !ws) return;

    const idx      = parseInt(document.getElementById('maneuverTimeSlider').value);
    const t        = sat.pts[idx]?.t ?? 0;
    const duration = parseFloat(document.getElementById('maneuverDuration').value) || 0;
    const direction = document.getElementById('maneuverDirection').value;

    const payload = {
        satName: currentSatName,
        triggerType: 'simTime',
        triggerValue: t,
        durationSec: duration,
        direction,
    };

    ws.send('MANEUVER_CREATE:' + JSON.stringify(payload));
    closeManeuverModal();
}