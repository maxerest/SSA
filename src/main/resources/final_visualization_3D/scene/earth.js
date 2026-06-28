// ================================================================
// scene/earth.js — Stars, Earth mesh, atmosphere
// ================================================================

import { EARTH_R } from '../core/config.js';

// Module-scoped so both buildLights() and setSunPosition() share it
const sunLight     = new THREE.DirectionalLight(0xfff8e7, 2);  // main sun — softer
const fillLight    = new THREE.DirectionalLight(0x4488ff, 0.3); // faint blue fill (space reflection)
const ambientLight = new THREE.AmbientLight(0x111122, 0.6);      // deep space ambient
export let initial_sun_position=[1, 0.5, 1];

export function buildStars(scene) {
    const N   = 3000;
    const pos = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) {
        const phi   = Math.acos(2 * Math.random() - 1);
        const theta = 2 * Math.PI * Math.random();
        const r     = EARTH_R * 40;
        pos[i*3]   = r * Math.sin(phi) * Math.cos(theta);
        pos[i*3+1] = r * Math.cos(phi);
        pos[i*3+2] = r * Math.sin(phi) * Math.sin(theta);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    scene.add(new THREE.Points(geo, new THREE.PointsMaterial({
        color: 0xffffff,
        size: EARTH_R * 0.003,
        sizeAttenuation: true,
    })));
}

export function buildLights(scene) {
    sunLight.position.set(1, 0.5, 1).normalize();

    // Fill light comes from the opposite side — softens the dark side
    fillLight.position.set(-1, -0.5, -1).normalize();

    scene.add(sunLight);
    scene.add(fillLight);
    scene.add(ambientLight);
}

export function setSunPosition(x, y, z) {
    initial_sun_position = [x, y, z];
    sunLight.position.set(x, y, z).normalize();
}

export function updateSunPosition(elapsedSeconds) {
    const angle = -(2 * Math.PI * elapsedSeconds) / 86400;
    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    const rx  =  initial_sun_position[0] * cos + initial_sun_position[2] * sin;
    const ry  =  initial_sun_position[1];
    const rz  = -initial_sun_position[0] * sin + initial_sun_position[2] * cos;
    sunLight.position.set(rx, ry, rz).normalize();
    fillLight.position.set(-rx, -ry, -rz).normalize();

}

export function buildEarth(scene) {
    const earthGeo = new THREE.SphereGeometry(EARTH_R, 64, 64);
    const earthMat = new THREE.MeshPhongMaterial({
        color: 0x1a6fa0,
        emissive: 0x0a1a2e,
        specular: 0x2244aa,
        shininess: 12,
    });

    const texLoader = new THREE.TextureLoader();
    texLoader.load('/scene/earth-blue-marble.jpg', tex => {
        earthMat.map = tex;
        earthMat.needsUpdate = true;
        window.GlobeInitialized = true;
    }, undefined, err => {
        console.error('[earth] Texture load failed:', err);
        window.GlobeInitialized = true;
    });

    const earthMesh = new THREE.Mesh(earthGeo, earthMat);
    earthMesh.rotation.y = -Math.PI / 2;
    scene.add(earthMesh);

    const atmMesh = new THREE.Mesh(
        new THREE.SphereGeometry(EARTH_R * 1.025, 64, 64),
        new THREE.MeshPhongMaterial({
            color: 0x3399ff,
            transparent: true,
            opacity: 0.05,
            side: THREE.FrontSide,
            depthWrite: false,
        })
    );
    atmMesh.rotation.y = -Math.PI / 2;
    scene.add(atmMesh);

    return { earthMesh, atmMesh };
}