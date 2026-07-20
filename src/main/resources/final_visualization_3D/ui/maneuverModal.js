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

// Camera orbit state
let camDistance = EARTH_R * 6;
let camTheta = 0;      // horizontal angle
let camPhi   = Math.PI / 3; // vertical angle
let isDragging = false;
let lastMouseX = 0, lastMouseY = 0;

export function setupManeuverModal(wsInstance) {
    ws = wsInstance;

    document.getElementById('maneuverBtn').addEventListener('click', () => {
        if (State.selectedSat) openManeuverModal(State.selectedSat);
    });
    document.getElementById('maneuverModalClose').addEventListener('click', closeManeuverModal);
    document.getElementById('maneuverCancelBtn').addEventListener('click', closeManeuverModal);
    document.getElementById('maneuverConfirmBtn').addEventListener('click', confirmManeuver);
    document.getElementById('maneuverTimeSlider').addEventListener('input', onSliderInput);

    document.getElementById('jumpApogee').addEventListener('click', () => jumpToOrbitPoint('apogee'));
    document.getElementById('jumpPerigee').addEventListener('click', () => jumpToOrbitPoint('perigee'));
    document.getElementById('jumpAscNode').addEventListener('click', () => jumpToOrbitPoint('ascNode'));
    document.getElementById('jumpDescNode').addEventListener('click', () => jumpToOrbitPoint('descNode'));

    document.getElementById('maneuverDirection').addEventListener('change', () => {
        document.getElementById('directionError').style.display = 'none';
    });
}
function setupCameraControls(canvas) {
    canvas.addEventListener('mousedown', e => {
        isDragging = true;
        lastMouseX = e.clientX;
        lastMouseY = e.clientY;
    });
    window.addEventListener('mouseup', () => { isDragging = false; });
    window.addEventListener('mousemove', e => {
        if (!isDragging) return;
        const dx = e.clientX - lastMouseX;
        const dy = e.clientY - lastMouseY;
        lastMouseX = e.clientX;
        lastMouseY = e.clientY;

        camTheta -= dx * 0.005;
        camPhi   -= dy * 0.005;
        // Clamp vertical angle so camera can't flip over the poles
        camPhi = Math.max(0.05, Math.min(Math.PI - 0.05, camPhi));
        updateCameraPosition();
    });

    canvas.addEventListener('wheel', e => {
        e.preventDefault();
        camDistance *= (1 + e.deltaY * 0.001);
        camDistance = Math.max(EARTH_R * 1.5, Math.min(EARTH_R * 30, camDistance));
        updateCameraPosition();
    }, { passive: false });
}
function updateCameraPosition() {
    if (!camera) return;
    camera.position.set(
        camDistance * Math.sin(camPhi) * Math.sin(camTheta),
        camDistance * Math.cos(camPhi),
        camDistance * Math.sin(camPhi) * Math.cos(camTheta)
    );
    camera.lookAt(0, 0, 0);
}
function buildMiniScene(canvas) {
    scene  = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(45, canvas.clientWidth / canvas.clientHeight, EARTH_R * 0.01, EARTH_R * 100);
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
    }, undefined, err => {
        console.error('[maneuverModal] Texture load failed:', err);
    });

    const earthMesh = new THREE.Mesh(earthGeo, earthMat);
    earthMesh.rotation.y = -Math.PI / 2;
    scene.add(earthMesh);

    satMesh = new THREE.Mesh(
        new THREE.SphereGeometry(EARTH_R * 0.1, 12, 12),
        new THREE.MeshBasicMaterial({ color: 0xffcc00 })
    );
    scene.add(satMesh);

    setupCameraControls(canvas);
    updateCameraPosition();
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
    document.getElementById('maneuverDirection').value = '';
    document.getElementById('directionError').style.display = 'none';

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

    const direction = document.getElementById('maneuverDirection').value;
    if (!direction) {
        document.getElementById('directionError').style.display = 'block';
        return; // block send entirely
    }

    const idx      = parseInt(document.getElementById('maneuverTimeSlider').value);
    const t        = sat.pts[idx]?.t ?? 0;
    const duration = parseFloat(document.getElementById('maneuverDuration').value) || 0;

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


/**
 * Find the index in sat.pts closest to a named orbital feature.
 * Apogee/perigee: radius extrema. Nodes: z crosses zero (equatorial plane),
 * ascending = z goes negative→positive, descending = positive→negative.
 */
function findOrbitPointIndex(sat, kind) {
    const pts = sat.pts;
    if (!pts.length) return 0;

    if (kind === 'apogee' || kind === 'perigee') {
        let bestIdx = 0;
        let bestR   = Math.sqrt(pts[0].x**2 + pts[0].y**2 + pts[0].z**2);
        for (let i = 1; i < pts.length; i++) {
            const r = Math.sqrt(pts[i].x**2 + pts[i].y**2 + pts[i].z**2);
            if ((kind === 'apogee' && r > bestR) || (kind === 'perigee' && r < bestR)) {
                bestR = r;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    // Node crossing: find first sign change of z
    for (let i = 1; i < pts.length; i++) {
        const prevZ = pts[i - 1].z;
        const currZ = pts[i].z;
        const crossesUp   = prevZ < 0 && currZ >= 0;
        const crossesDown = prevZ > 0 && currZ <= 0;
        if (kind === 'ascNode' && crossesUp)   return i;
        if (kind === 'descNode' && crossesDown) return i;
    }
    return 0; // no crossing found in this data window (e.g. equatorial orbit, or window too short)
}

function jumpToOrbitPoint(kind) {
    const sat = State.sats[currentSatName];
    if (!sat) return;
    const idx = findOrbitPointIndex(sat, kind);
    document.getElementById('maneuverTimeSlider').value = idx;
    updateSatMeshPosition(sat, idx);
}
