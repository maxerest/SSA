// ================================================================
// scene/camera.js — Camera + mouse/touch orbit controls
// ================================================================

import { EARTH_R } from '../core/config.js';

export function buildCamera(canvas) {
    const camera = new THREE.PerspectiveCamera(45, 1, 1e4, 1e11);
    camera.position.set(0, 0, EARTH_R * 3.2);

    let isDragging = false;
    let prevMouse  = { x: 0, y: 0 };
    let touchStart = null;
    const spherical = { theta: 0, phi: Math.PI / 2, r: EARTH_R * 3.2 };

    function updateCamera() {
        camera.position.set(
            spherical.r * Math.sin(spherical.phi) * Math.sin(spherical.theta),
            spherical.r * Math.cos(spherical.phi),
            spherical.r * Math.sin(spherical.phi) * Math.cos(spherical.theta)
        );
        camera.lookAt(0, 0, 0);
    }

    canvas.addEventListener('mousedown',  e => { isDragging = true; prevMouse = { x: e.clientX, y: e.clientY }; });
    canvas.addEventListener('mouseup',    () => isDragging = false);
    canvas.addEventListener('mouseleave', () => isDragging = false);
    canvas.addEventListener('mousemove',  e => {
        if (!isDragging) return;
        spherical.theta -= (e.clientX - prevMouse.x) * 0.005;
        spherical.phi    = Math.max(0.1, Math.min(Math.PI - 0.1, spherical.phi + (e.clientY - prevMouse.y) * 0.005));
        prevMouse = { x: e.clientX, y: e.clientY };
        updateCamera();
    });
    canvas.addEventListener('wheel', e => {
        spherical.r = Math.max(EARTH_R * 1.5, Math.min(EARTH_R * 50, spherical.r * (1 + e.deltaY * 0.001)));
        updateCamera();
        e.preventDefault();
    }, { passive: false });

    canvas.addEventListener('touchstart', e => {
        if (e.touches.length === 1) touchStart = { x: e.touches[0].clientX, y: e.touches[0].clientY };
    });
    canvas.addEventListener('touchmove', e => {
        if (!touchStart || e.touches.length !== 1) return;
        spherical.theta -= (e.touches[0].clientX - touchStart.x) * 0.005;
        spherical.phi    = Math.max(0.1, Math.min(Math.PI - 0.1, spherical.phi + (e.touches[0].clientY - touchStart.y) * 0.005));
        touchStart = { x: e.touches[0].clientX, y: e.touches[0].clientY };
        updateCamera();
        e.preventDefault();
    }, { passive: false });

    updateCamera();

    return { camera, isDraggingRef: () => isDragging };
}