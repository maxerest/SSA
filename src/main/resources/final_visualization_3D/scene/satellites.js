// ================================================================
// scene/satellites.js — Three.js objects for sats & ground stations
// ================================================================

import { EARTH_R, TRAIL_LENGTH } from '../core/config.js';
import { State }                  from '../core/state.js';
import { ecefToThreeJS, latLonToECEF, getSatColor, closestPoint } from '../core/utils.js';

/** @type {Record<string, { mesh, trailPts, trailGeo, trailArr, history, colorHex }>} */
export const satObjects = {};

/** @type {Array<{ name, sphere, cone, activated }>} */
export const gsObjects = [];

/** Maps THREE mesh uuid → satellite name for raycasting */
export const satMeshMap = new Map();

/**
 * @param {THREE.Scene} scene
 * @param {string} satName
 * @param {string} colorHex
 */
export function createSatObjects(scene, satName, colorHex) {
    const color = new THREE.Color(colorHex);
    const mesh  = new THREE.Mesh(
        new THREE.SphereGeometry(100_000, 12, 12),
        new THREE.MeshPhongMaterial({ color, emissive: color.clone().multiplyScalar(0.3), shininess: 60 })
    );
    scene.add(mesh);
    satMeshMap.set(mesh.uuid, satName);

    const ring = new THREE.Mesh(
        new THREE.SphereGeometry(175_000, 48, 48),
        new THREE.MeshBasicMaterial({
            color: colorHex,
            transparent: true,
            opacity: 0.35,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
            side: THREE.DoubleSide
        })
    );
    ring.visible = false;
    scene.add(ring);
    const trailArr = new Float32Array(TRAIL_LENGTH * 3);
    const trailGeo = new THREE.BufferGeometry();
    trailGeo.setAttribute('position', new THREE.BufferAttribute(trailArr, 3));
    const trailPts = new THREE.Points(trailGeo, new THREE.PointsMaterial({
        color, size: 50_000, sizeAttenuation: true, transparent: true, opacity: 0.55,
    }));
    trailPts.visible = State.showTrails;
    scene.add(trailPts);

    satObjects[satName] = { mesh, trailPts, trailGeo, trailArr, history: [], colorHex,ring };
}

/**
 * @param {THREE.Scene} scene
 * @param {string} satName
 */
export function removeSatObjects(scene, satName) {
    const obj = satObjects[satName];
    if (!obj) return;
    scene.remove(obj.mesh);
    scene.remove(obj.trailPts);
    scene.remove(obj.ring);
    satMeshMap.delete(obj.mesh.uuid);
    delete satObjects[satName];
}

function buildConeMesh(ecefPos, activated) {
    const outward   = new THREE.Vector3(...ecefPos).normalize();
    const top       = new THREE.Vector3(...ecefPos).addScaledVector(outward, 2_000_000);
    const radius    = 2_000_000 * Math.tan(75 * Math.PI / 180);
    const N         = 48;
    const arbitrary = Math.abs(outward.z) < 0.9 ? new THREE.Vector3(0,0,1) : new THREE.Vector3(1,0,0);
    const u = new THREE.Vector3().crossVectors(outward, arbitrary).normalize();
    const v = new THREE.Vector3().crossVectors(outward, u);

    const pts = [];
    for (let i = 0; i < N; i++) {
        const a = (2 * Math.PI * i) / N;
        pts.push(top.clone().addScaledVector(u, radius * Math.cos(a)).addScaledVector(v, radius * Math.sin(a)));
    }
    const tip   = new THREE.Vector3(...ecefPos);
    const verts = [];
    for (let i = 0; i < N; i++) {
        const next = (i + 1) % N;
        verts.push(tip, pts[i], pts[next]);
    }
    const geo = new THREE.BufferGeometry();
    const arr = new Float32Array(verts.length * 3);
    verts.forEach((v3, i) => { arr[i*3] = v3.x; arr[i*3+1] = v3.y; arr[i*3+2] = v3.z; });
    geo.setAttribute('position', new THREE.BufferAttribute(arr, 3));
    return new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
        color: activated ? 0x00cc66 : 0xff3333,
        transparent: true, opacity: 0.18, side: THREE.DoubleSide, depthWrite: false,
    }));
}

/**
 * @param {THREE.Scene} scene
 */
export function addGroundStation(scene, name, lat, lon, alt, activated) {
    const [x, y, z] = ecefToThreeJS(latLonToECEF(lat, lon, alt * 1000));
    const sphere = new THREE.Mesh(
        new THREE.SphereGeometry(80_000, 16, 16),
        new THREE.MeshPhongMaterial({
            color:   activated ? 0x00ff88 : 0xff4444,
            emissive: activated ? 0x003322 : 0x220000,
        })
    );
    sphere.position.set(x, y, z);
    scene.add(sphere);

    const cone = buildConeMesh([x, y, z], activated);
    cone.visible = State.showCones;
    scene.add(cone);

    gsObjects.push({ name, sphere, cone, activated });
}

/**
 * @param {THREE.Scene} scene
 */
export function clearGroundStations(scene) {
    gsObjects.forEach(o => { scene.remove(o.sphere); scene.remove(o.cone); });
    gsObjects.length = 0;
}

/**
 * Update all satellite meshes for the current time step.
 */
export function updateSatPositions() {
    const t        = State.times[State.idx];
    const satNames = Object.keys(State.sats);

    satNames.forEach(satName => {
        const sat = State.sats[satName];
        const obj = satObjects[satName];
        if (!obj) return;

        const firstT = sat.pts[0].t;
        const lastT  = sat.pts[sat.pts.length - 1].t;

        if (t < firstT || t > lastT) {
            obj.mesh.visible     = false;
            obj.ring.visible      = false;
            obj.trailPts.visible = false;
            return;
        }
        if (obj.ring.visible) {
            const pulse = (Math.sin(t * 3) + 1) / 2;

            const scale = 1 + pulse * 0.12;
            obj.ring.scale.setScalar(scale);

            obj.ring.material.opacity = 0.2 + pulse * 0.35;

            obj.ring.rotation.y += 0.005;
        }


        obj.mesh.visible     = true;
        obj.trailPts.visible = State.showTrails;

        const closest      = closestPoint(sat.pts, t);
        const [tx, ty, tz] = ecefToThreeJS([closest.x, closest.y, closest.z]);
        obj.mesh.position.set(tx, ty, tz);
        obj.ring.position.set(tx, ty, tz);
        obj.ring.lookAt(0, 0, 0);

        const hexColor = getSatColor(sat, closest);
        obj.mesh.material.color.set(hexColor);
        obj.mesh.material.emissive.set(hexColor).multiplyScalar(0.25);
        obj.trailPts.material.color.set(hexColor);

        obj.history.unshift(ecefToThreeJS([closest.x, closest.y, closest.z]));
        if (obj.history.length > TRAIL_LENGTH) obj.history.length = TRAIL_LENGTH;
        for (let i = 0; i < TRAIL_LENGTH; i++) {
            const p = obj.history[i] || obj.history[obj.history.length - 1] || [0, 0, 0];
            obj.trailArr[i*3] = p[0]; obj.trailArr[i*3+1] = p[1]; obj.trailArr[i*3+2] = p[2];
        }
        obj.trailGeo.attributes.position.needsUpdate = true;
    });
}
export function setSatSelection(satName) {
    Object.entries(satObjects).forEach(([name, obj]) => {
        obj.ring.visible = name === satName;
    });
}