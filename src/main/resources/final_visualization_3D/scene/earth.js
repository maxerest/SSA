// ================================================================
// scene/earth.js — Stars, Earth mesh, atmosphere
// ================================================================

import { EARTH_R } from '../core/config.js';
import { latLonToECEF, ecefToThreeJS } from '../core/utils.js';

// Module-scoped so both buildLights() and setSunPosition() share it
const sunLight     = new THREE.DirectionalLight(0xfff8e7, 2);  // main sun — softer
const fillLight    = new THREE.DirectionalLight(0x4488ff, 0.3); // faint blue fill (space reflection)
const ambientLight = new THREE.AmbientLight(0x111122, 0.6);      // deep space ambient
export let initial_sun_position=[1, 0.5, 1];
const zoneGroup    = new THREE.Group();   // ← module-scoped, shared
zoneGroup.name = 'eoZones';

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
    earthMesh.rotation.y = -Math.PI/2;
    scene.add(earthMesh);

    const atmMesh = new THREE.Mesh(
        new THREE.SphereGeometry(EARTH_R * 1.025, 64, 64),
        new THREE.MeshPhongMaterial({
            color: 0x3399ff,
            transparent: true,
            opacity: 0.05,
            side: THREE.FrontSide,
        })
    );
    scene.add(atmMesh);
    return { earthMesh, atmMesh };
}
function createLabelSprite(text) {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    const fontSize = 32;
    ctx.font = `bold ${fontSize}px sans-serif`;
    const textWidth = ctx.measureText(text).width;

    canvas.width = textWidth + 20;
    canvas.height = fontSize + 20;

    ctx.font = `bold ${fontSize}px sans-serif`;
    ctx.fillStyle = 'rgba(0,0,0,0.5)';
    ctx.fillStyle = '#ffffff';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 10, canvas.height / 2);

    const texture = new THREE.CanvasTexture(canvas);
    const material = new THREE.SpriteMaterial({ map: texture, depthTest: true, transparent: true });
    const sprite = new THREE.Sprite(material);

    const aspect = canvas.width / canvas.height;
    const labelHeight = EARTH_R * 0.08;
    sprite.scale.set(labelHeight * aspect, labelHeight, 1);

    return sprite;
}
export function renderZones(scene, zones) {
    zoneGroup.clear();

    const SURFACE_R          = EARTH_R;
    const SEGMENTS            = 12;
    const MIN_VISIBLE_SIZE    = EARTH_R * 0.008; // below this diagonal, render as a fixed-size dot instead of to-scale mesh

    const lerp = (a, b, t) => a + (b - a) * t;

    zones.forEach(zone => {
        if (!zone.corners || zone.corners.length < 4 || zone.corners.some(c => isNaN(c.lat) || isNaN(c.lon))) {
            console.warn('[earth] Skipping malformed zone:', zone.name, zone.corners);
            return;
        }

        const [c0, c1, c2, c3] = zone.corners;

        // Build a SEGMENTS x SEGMENTS grid of lat/lon, bilinearly interpolated across the 4 corners,
        // then project each point individually so the surface follows the globe's curvature.
        const grid = [];
        for (let i = 0; i <= SEGMENTS; i++) {
            const v = i / SEGMENTS;
            const row = [];
            for (let j = 0; j <= SEGMENTS; j++) {
                const u = j / SEGMENTS;
                const lat = lerp(lerp(c0.lat, c1.lat, u), lerp(c3.lat, c2.lat, u), v);
                const lon = lerp(lerp(c0.lon, c1.lon, u), lerp(c3.lon, c2.lon, u), v);
                const [x, y, z] = ecefToThreeJS(latLonToECEF(lat, lon, 0));
                row.push(new THREE.Vector3(x, y, z).setLength(SURFACE_R));
            }
            grid.push(row);
        }

        const flatGrid = grid.flat();
        const diag = grid[0][0].distanceTo(grid[SEGMENTS][SEGMENTS]);

        if (diag < MIN_VISIBLE_SIZE) {
            // Too small to render to-scale at global zoom — show as a fixed-size marker instead
            const dot = new THREE.Mesh(
                new THREE.SphereGeometry(MIN_VISIBLE_SIZE * 0.5, 8, 8),
                new THREE.MeshBasicMaterial({ color: 0xfbb942 })
            );
            dot.position.copy(grid[0][0]);
            dot.userData.zoneName = zone.name;
            zoneGroup.add(dot);
        } else {
            // Large enough — build the curved, subdivided rectangle mesh
            const verts = [];
            for (let i = 0; i < SEGMENTS; i++) {
                for (let j = 0; j < SEGMENTS; j++) {
                    const a = grid[i][j], b = grid[i][j+1], c = grid[i+1][j], d = grid[i+1][j+1];
                    verts.push(a.x,a.y,a.z, b.x,b.y,b.z, c.x,c.y,c.z);
                    verts.push(b.x,b.y,b.z, d.x,d.y,d.z, c.x,c.y,c.z);
                }
            }
            const geo = new THREE.BufferGeometry();
            geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(verts), 3));
            geo.computeVertexNormals();

            const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
                color: 0xfbb942,
                side: THREE.DoubleSide,
            }));
            mesh.userData.zoneName = zone.name;
            zoneGroup.add(mesh);
        }

        // Label — centroid of the grid, floated above the surface
        const centroid = new THREE.Vector3();
        flatGrid.forEach(p => centroid.add(p));
        centroid.divideScalar(flatGrid.length);
        centroid.setLength(SURFACE_R + EARTH_R * 0.05);

        const label = createLabelSprite(zone.name);
        label.position.copy(centroid);
        zoneGroup.add(label);
    });

    scene.add(zoneGroup);
}
