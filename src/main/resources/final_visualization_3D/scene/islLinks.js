// ================================================================
// scene/islLinks.js — Inter-satellite link visualization
// ================================================================

import { State } from '../core/state.js';
import { satObjects } from './satellites.js';

let scene = null;

/*
 * Stores currently displayed THREE.Line objects.
 *
 * key -> THREE.Line
 */
const activeISLLines = new Map();


export function setISLScene(threeScene) {
    scene = threeScene;
}


/**
 * Current absolute simulation time in milliseconds.
 */
function getCurrentSimulationTime() {

    if (State.obsEpoch === null) {
        return null;
    }

    if (!State.times.length) {
        return null;
    }

    const elapsedSeconds = State.times[State.idx];

    return State.obsEpoch + elapsedSeconds * 1000;
}


/**
 * Unique key for an ISL event.
 */
function getLinkKey(link) {

    /*
     * Sort satellite names so SAT1 -> SAT2 and SAT2 -> SAT1
     * don't accidentally produce two identical lines.
     */
    const pair = [
        link.sat_ref,
        link.sat_linked
    ].sort().join('__');

    return `${pair}_${link.start.getTime()}_${link.end.getTime()}`;
}


/**
 * Create a yellow THREE.js line.
 */
function createISLLine() {

    const geometry = new THREE.BufferGeometry();

    const positions = new Float32Array(6);

    geometry.setAttribute(
        'position',
        new THREE.BufferAttribute(positions, 3)
    );

    const material = new THREE.LineBasicMaterial({
        color: 0xffff00
    });

    const line = new THREE.Line(
        geometry,
        material
    );

    /*
     * Positions change every simulation tick, so don't let
     * Three.js incorrectly cull the moving line.
     */
    line.frustumCulled = false;

    scene.add(line);

    return line;
}


/**
 * Remove and dispose one line.
 */
function removeISLLine(key) {

    const line = activeISLLines.get(key);

    if (!line) {
        return;
    }

    scene.remove(line);

    line.geometry.dispose();
    line.material.dispose();

    activeISLLines.delete(key);
}


/**
 * Called every simulation tick.
 *
 * Displays an ISL when:
 *
 *     start <= simulationTime <= end
 *
 */
export function updateISLLinks() {

    if (!scene) {
        return;
    }

    const currentTime = getCurrentSimulationTime();

    if (currentTime === null) {
        return;
    }

    const bySat = State.get('ISLSat') || {};

    /*
     * Tracks which lines should still exist after this update.
     */
    const linksActiveNow = new Set();


    Object.values(bySat).forEach(links => {

        links.forEach(link => {

            const startTime = link.start.getTime();
            const endTime   = link.end.getTime();

            /*
             * Invalid CSV date
             */
            if (
                Number.isNaN(startTime) ||
                Number.isNaN(endTime)
            ) {
                return;
            }

            /*
             * ISL isn't active at this simulation time.
             */
            if (
                currentTime < startTime ||
                currentTime > endTime
            ) {
                return;
            }


            /*
             * Find the actual Three.js satellite objects.
             */
            const satReference =
                satObjects[link.sat_ref];

            const satTarget =
                satObjects[link.sat_linked];


            if (!satReference || !satTarget) {

                console.warn(
                    '[ISL] Satellite object not found:',
                    link.sat_ref,
                    link.sat_linked
                );

                return;
            }


            /*
             * Don't draw links to satellites that aren't currently
             * visible in the propagated interval.
             */
            if (
                !satReference.mesh.visible ||
                !satTarget.mesh.visible
            ) {
                return;
            }


            const key = getLinkKey(link);

            linksActiveNow.add(key);


            /*
             * Create line only once.
             */
            let line = activeISLLines.get(key);

            if (!line) {

                line = createISLLine();

                activeISLLines.set(
                    key,
                    line
                );
            }


            /*
             * Update line endpoints to current satellite positions.
             */
            const positions =
                line.geometry.attributes.position.array;


            positions[0] = satReference.mesh.position.x;
            positions[1] = satReference.mesh.position.y;
            positions[2] = satReference.mesh.position.z;

            positions[3] = satTarget.mesh.position.x;
            positions[4] = satTarget.mesh.position.y;
            positions[5] = satTarget.mesh.position.z;


            line.geometry.attributes.position.needsUpdate = true;
        });
    });


    /*
     * Remove links whose time interval has finished.
     */
    for (const key of activeISLLines.keys()) {

        if (!linksActiveNow.has(key)) {
            removeISLLine(key);
        }
    }
}


/**
 * Optional cleanup.
 */
export function clearISLLinks() {

    for (const key of [...activeISLLines.keys()]) {
        removeISLLine(key);
    }
}