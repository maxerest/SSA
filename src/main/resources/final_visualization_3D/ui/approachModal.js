
// ================================================================
// ui/approachModal.js — Satellite approach modal (3D preview)
// ================================================================

import { State } from '../core/state.js';
import { EARTH_R } from '../core/config.js';
import { ecefToThreeJS } from '../core/utils.js';

let ws = null;

let scene, camera, renderer;
let earthMesh;
let chaserMesh, targetMesh;
let chaserOrbitLine = null;
let targetOrbitLine = null;

let animId = null;
let currentChaserName = null;

let camDistance = EARTH_R * 6;
let camTheta = 0;
let camPhi = Math.PI / 3;

let isDragging = false;
let lastMouseX = 0;
let lastMouseY = 0;


// ================================================================
// INITIALIZATION
// ================================================================

export function setupApproachModal(wsInstance) {

    ws = wsInstance;

    document.getElementById('approachBtn')
        .addEventListener('click', () => {
            if (State.selectedSat) {
                openApproachModal(State.selectedSat);
            }
        });

    document.getElementById('approachModalClose')
        .addEventListener('click', closeApproachModal);

    document.getElementById('approachCancelBtn')
        .addEventListener('click', closeApproachModal);

    document.getElementById('approachConfirmBtn')
        .addEventListener('click', confirmApproach);

    document.getElementById('approachTimeSlider')
        .addEventListener('input', onSliderInput);

    document.getElementById('approachTargetSelect')
        .addEventListener('change', onTargetChange);

    document.getElementById('approachJumpApogee')
        .addEventListener('click', () =>
            jumpToOrbitPoint('apogee'));

    document.getElementById('approachJumpPerigee')
        .addEventListener('click', () =>
            jumpToOrbitPoint('perigee'));

    document.getElementById('approachJumpAscNode')
        .addEventListener('click', () =>
            jumpToOrbitPoint('ascNode'));

    document.getElementById('approachJumpDescNode')
        .addEventListener('click', () =>
            jumpToOrbitPoint('descNode'));

    window.addEventListener('resize', () => {
        if (renderer &&
            document.getElementById('approachModal')
                .style.display === 'flex') {
            resizeCanvas(
                document.getElementById('approachCanvas')
            );
        }
    });
}


// ================================================================
// CAMERA CONTROLS
// ================================================================

function setupCameraControls(canvas) {

    canvas.addEventListener('mousedown', e => {
        isDragging = true;
        lastMouseX = e.clientX;
        lastMouseY = e.clientY;
    });

    window.addEventListener('mouseup', () => {
        isDragging = false;
    });

    window.addEventListener('mousemove', e => {

        if (!isDragging) return;

        const dx = e.clientX - lastMouseX;
        const dy = e.clientY - lastMouseY;

        lastMouseX = e.clientX;
        lastMouseY = e.clientY;

        camTheta -= dx * 0.005;
        camPhi -= dy * 0.005;

        camPhi = Math.max(
            0.05,
            Math.min(Math.PI - 0.05, camPhi)
        );

        updateCameraPosition();
    });

    canvas.addEventListener('wheel', e => {

        e.preventDefault();

        camDistance *= (1 + e.deltaY * 0.001);

        camDistance = Math.max(
            EARTH_R * 1.5,
            Math.min(EARTH_R * 30, camDistance)
        );

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


// ================================================================
// BUILD THREE.JS SCENE
// ================================================================

function buildMiniScene(canvas) {

    scene = new THREE.Scene();

    camera = new THREE.PerspectiveCamera(
        45,
        canvas.clientWidth / Math.max(canvas.clientHeight, 1),
        EARTH_R * 0.01,
        EARTH_R * 100
    );

    renderer = new THREE.WebGLRenderer({
        canvas,
        antialias: true,
        alpha: true
    });

    renderer.setPixelRatio(
        Math.min(window.devicePixelRatio || 1, 2)
    );

    // Lighting
    const light = new THREE.DirectionalLight(0xffffff, 1.2);
    light.position.set(1, 1, 1);
    scene.add(light);

    scene.add(
        new THREE.AmbientLight(0x334455, 0.8)
    );

    // Earth
    const earthGeo = new THREE.SphereGeometry(
        EARTH_R, 64, 64
    );

    const earthMat = new THREE.MeshPhongMaterial({
        color: 0x1a6fa0,
        emissive: 0x0a1a2e,
        specular: 0x2244aa,
        shininess: 12
    });

    const texLoader = new THREE.TextureLoader();

    texLoader.load(
        '/scene/earth-blue-marble.jpg',
        tex => {
            earthMat.map = tex;
            earthMat.needsUpdate = true;
        },
        undefined,
        err => {
            console.error(
                '[approachModal] Texture load failed:',
                err
            );
        }
    );

    earthMesh = new THREE.Mesh(earthGeo, earthMat);
    earthMesh.rotation.y = -Math.PI / 2;
    scene.add(earthMesh);

    // Chaser satellite (yellow)
    chaserMesh = new THREE.Mesh(
        new THREE.SphereGeometry(EARTH_R * 0.08, 12, 12),
        new THREE.MeshBasicMaterial({
            color: 0xffcc00
        })
    );

    scene.add(chaserMesh);

    // Target satellite (cyan)
    targetMesh = new THREE.Mesh(
        new THREE.SphereGeometry(EARTH_R * 0.08, 12, 12),
        new THREE.MeshBasicMaterial({
            color: 0x00e5ff
        })
    );

    targetMesh.visible = false;
    scene.add(targetMesh);

    setupCameraControls(canvas);
    updateCameraPosition();
}


// ================================================================
// ORBIT TRAJECTORIES
// ================================================================

function removeOrbitLine(line) {

    if (!line) return;

    scene.remove(line);
    line.geometry.dispose();
    line.material.dispose();
}


function createOrbitLine(sat, color) {

    if (!sat?.pts || sat.pts.length < 2) {
        return null;
    }

    const pts = sat.pts
        .filter(p =>
            Number.isFinite(p.x) &&
            Number.isFinite(p.y) &&
            Number.isFinite(p.z)
        )
        .map(p => {

            const [x, y, z] = ecefToThreeJS([
                p.x, p.y, p.z
            ]);

            return new THREE.Vector3(x, y, z);
        });

    if (pts.length < 2) return null;

    const geometry = new THREE.BufferGeometry()
        .setFromPoints(pts);

    const material = new THREE.LineBasicMaterial({
        color
    });

    // Line instead of LineLoop:
    // avoids connecting the last point to the first.
    const line = new THREE.Line(geometry, material);

    scene.add(line);

    return line;
}


function buildOrbitLines(chaser, target) {

    removeOrbitLine(chaserOrbitLine);
    removeOrbitLine(targetOrbitLine);

    chaserOrbitLine = createOrbitLine(
        chaser,
        0xffcc00
    );

    targetOrbitLine = target
        ? createOrbitLine(target, 0x00e5ff)
        : null;
}


// ================================================================
// TIME SYNCHRONIZATION
// ================================================================

// Assumes sat.pts is ordered by increasing simulation time.
//
// Interpolates the satellite's ECEF position at time t.
// This is intended for visualization.

function getPositionAtTime(sat, t) {

    const pts = sat?.pts;

    if (!pts?.length || !Number.isFinite(t)) {
        return null;
    }

    const first = pts[0];
    const last = pts[pts.length - 1];

    if (t < first.t || t > last.t) {
        return null;
    }

    if (t === first.t) return first;
    if (t === last.t) return last;

    // Binary search
    let low = 0;
    let high = pts.length - 1;

    while (high - low > 1) {

        const mid = Math.floor((low + high) / 2);

        if (pts[mid].t <= t) {
            low = mid;
        } else {
            high = mid;
        }
    }

    const p0 = pts[low];
    const p1 = pts[high];

    const dt = p1.t - p0.t;

    if (dt <= 0) return p0;

    const alpha = (t - p0.t) / dt;

    return {
        x: p0.x + alpha * (p1.x - p0.x),
        y: p0.y + alpha * (p1.y - p0.y),
        z: p0.z + alpha * (p1.z - p0.z),
        t
    };
}


// Return the chaser index range for which both trajectories
// have valid data.

function getCommonTimeRange(chaser, target) {

    const chaserPts = chaser?.pts;
    const targetPts = target?.pts;

    if (!chaserPts?.length || !targetPts?.length) {
        return null;
    }

    const startTime = Math.max(
        chaserPts[0].t,
        targetPts[0].t
    );

    const endTime = Math.min(
        chaserPts[chaserPts.length - 1].t,
        targetPts[targetPts.length - 1].t
    );

    if (startTime > endTime) return null;

    // Find indices of the chaser within the overlap.
    const firstIndex = chaserPts.findIndex(
        p => p.t >= startTime
    );

    let lastIndex = -1;

    for (let i = chaserPts.length - 1; i >= 0; i--) {
        if (chaserPts[i].t <= endTime) {
            lastIndex = i;
            break;
        }
    }

    if (firstIndex < 0 || lastIndex < firstIndex) {
        return null;
    }

    return {
        min: firstIndex,
        max: lastIndex
    };
}


// ================================================================
// UPDATE SATELLITE POSITIONS
// ================================================================

function updateSatellitePositions(time) {

    const chaser = State.sats[currentChaserName];

    const targetName =
        document.getElementById('approachTargetSelect').value;

    const target = State.sats[targetName];

    if (!chaser) return;

    // Chaser position
    const chaserPoint = getPositionAtTime(
        chaser, time
    );

    if (chaserPoint) {

        const [x, y, z] = ecefToThreeJS([
            chaserPoint.x,
            chaserPoint.y,
            chaserPoint.z
        ]);

        chaserMesh.position.set(x, y, z);
        chaserMesh.visible = true;

    } else {
        chaserMesh.visible = false;
    }

    // Target position at the SAME simulation time
    const targetPoint = getPositionAtTime(
        target, time
    );

    if (targetPoint) {

        const [x, y, z] = ecefToThreeJS([
            targetPoint.x,
            targetPoint.y,
            targetPoint.z
        ]);

        targetMesh.position.set(x, y, z);
        targetMesh.visible = true;

    } else {
        targetMesh.visible = false;
    }
}


function updatePreviewFromSlider() {

    const chaser = State.sats[currentChaserName];

    if (!chaser?.pts?.length) return;

    const slider =
        document.getElementById('approachTimeSlider');

    const index = Number(slider.value);
    const point = chaser.pts[index];

    if (!point) return;

    const t = point.t;

    document.getElementById('approachTimeLabel')
        .textContent = `T = ${t.toFixed(0)} s`;

    updateSatellitePositions(t);
}


// ================================================================
// TARGET SELECTION
// ================================================================

function onTargetChange() {

    const chaser = State.sats[currentChaserName];

    const targetName =
        document.getElementById('approachTargetSelect').value;

    const target = State.sats[targetName];

    if (!chaser) return;

    buildOrbitLines(chaser, target);

    refreshTimeSlider();

    updatePreviewFromSlider();
}


// ================================================================
// SLIDER
// ================================================================

function refreshTimeSlider() {

    const chaser = State.sats[currentChaserName];

    const targetName =
        document.getElementById('approachTargetSelect').value;

    const target = State.sats[targetName];

    const slider =
        document.getElementById('approachTimeSlider');

    const confirmBtn =
        document.getElementById('approachConfirmBtn');

    const error =
        document.getElementById('approachError');

    error.textContent = '';

    if (!chaser?.pts?.length) {

        slider.disabled = true;
        confirmBtn.disabled = true;
        error.textContent = 'No chaser trajectory available.';
        return;
    }

    let min = 0;
    let max = chaser.pts.length - 1;

    if (targetName) {

        const range = getCommonTimeRange(
            chaser, target
        );

        if (!range) {

            slider.disabled = true;
            confirmBtn.disabled = true;

            error.textContent =
                'The satellites have no common trajectory time window.';

            targetMesh.visible = false;
            return;
        }

        min = range.min;
        max = range.max;
    }

    const previous = Number(slider.value);

    slider.min = min;
    slider.max = max;

    slider.value = Math.max(
        min,
        Math.min(max, previous)
    );

    slider.disabled = false;

    confirmBtn.disabled = !targetName;
}


function onSliderInput() {
    updatePreviewFromSlider();
}


// ================================================================
// OPEN APPROACH MODAL
// ================================================================

function openApproachModal(chaserName) {

    const chaser = State.sats[chaserName];

    if (!chaser) return;

    currentChaserName = chaserName;

    document.getElementById('approachModal')
        .style.display = 'flex';

    document.getElementById('approachChaserName')
        .textContent = chaserName;

    document.getElementById('approachError')
        .textContent = '';

    // Populate target dropdown
    const select =
        document.getElementById('approachTargetSelect');

    select.replaceChildren(
        new Option('Select target', '')
    );

    Object.keys(State.sats)
        .filter(name => name !== chaserName)
        .sort()
        .forEach(name => {
            select.add(new Option(name, name));
        });

    select.value = '';

    // Build 3D scene once
    const canvas =
        document.getElementById('approachCanvas');

    if (!scene) {
        buildMiniScene(canvas);
    }

    // Initially show only the chaser orbit
    buildOrbitLines(chaser, null);

    const slider =
        document.getElementById('approachTimeSlider');

    slider.value = 0;

    refreshTimeSlider();
    updatePreviewFromSlider();

    resizeCanvas(canvas);

    if (animId === null) {
        animate();
    }
}


// ================================================================
// RENDER LOOP
// ================================================================

function resizeCanvas(canvas) {

    if (!renderer || !camera) return;

    const w = canvas.clientWidth;
    const h = canvas.clientHeight;

    if (w <= 0 || h <= 0) return;

    renderer.setSize(w, h, false);

    camera.aspect = w / h;
    camera.updateProjectionMatrix();
}


function animate() {

    animId = requestAnimationFrame(animate);

    if (renderer && scene && camera) {
        renderer.render(scene, camera);
    }
}


// ================================================================
// CLOSE MODAL
// ================================================================

function closeApproachModal() {

    document.getElementById('approachModal')
        .style.display = 'none';

    isDragging = false;
    currentChaserName = null;

    if (animId !== null) {
        cancelAnimationFrame(animId);
        animId = null;
    }
}


// ================================================================
// CONFIRM APPROACH
// ================================================================

function confirmApproach() {

    if (!ws || !currentChaserName) return;

    const chaserName = currentChaserName;

    const targetName =
        document.getElementById('approachTargetSelect').value;

    if (!targetName ||
        targetName === chaserName ||
        !State.sats[targetName]) {

        showApproachError(
            'Please select a valid target satellite.'
        );

        return;
    }

    const chaser = State.sats[chaserName];
    const target = State.sats[targetName];

    const slider =
        document.getElementById('approachTimeSlider');

    const index = Number(slider.value);

    const point = chaser?.pts?.[index];

    if (!point || !Number.isFinite(point.t) ||
        !getPositionAtTime(target, point.t)) {

        showApproachError(
            'Invalid or unavailable approach start time.'
        );

        return;
    }

    const payload = {
        chaserSatName: chaserName,
        targetSatName: targetName,
        triggerType: 'simTime',
        triggerValue: point.t
    };

    ws.send(
        'APPROACH_CREATE:' + JSON.stringify(payload)
    );

    closeApproachModal();
}


function showApproachError(message) {

    document.getElementById('approachError')
        .textContent = message;
}


// ================================================================
// ORBITAL POINT SHORTCUTS
// ================================================================

function findOrbitPointIndex(sat, kind, minIndex, maxIndex) {

    const pts = sat.pts;

    if (!pts?.length) return 0;

    if (kind === 'apogee' || kind === 'perigee') {

        let bestIdx = minIndex;

        let bestR = Math.hypot(
            pts[minIndex].x,
            pts[minIndex].y,
            pts[minIndex].z
        );

        for (let i = minIndex + 1; i <= maxIndex; i++) {

            const r = Math.hypot(
                pts[i].x,
                pts[i].y,
                pts[i].z
            );

            if (
                (kind === 'apogee' && r > bestR) ||
                (kind === 'perigee' && r < bestR)
            ) {
                bestR = r;
                bestIdx = i;
            }
        }

        return bestIdx;
    }

    // Equatorial node crossing
    for (let i = Math.max(1, minIndex); i <= maxIndex; i++) {

        const prevZ = pts[i - 1].z;
        const currZ = pts[i].z;

        const crossesUp =
            prevZ < 0 && currZ >= 0;

        const crossesDown =
            prevZ > 0 && currZ <= 0;

        if (kind === 'ascNode' && crossesUp) {
            return i;
        }

        if (kind === 'descNode' && crossesDown) {
            return i;
        }
    }

    return minIndex;
}


function jumpToOrbitPoint(kind) {

    const chaser = State.sats[currentChaserName];

    if (!chaser?.pts?.length) return;

    const slider =
        document.getElementById('approachTimeSlider');

    if (slider.disabled) return;

    const min = Number(slider.min);
    const max = Number(slider.max);

    const index = findOrbitPointIndex(
        chaser, kind, min, max
    );

    slider.value = index;

    updatePreviewFromSlider();
}
