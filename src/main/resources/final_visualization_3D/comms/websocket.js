// ================================================================
// comms/websocket.js — WebSocket connection & message routing
// ================================================================

import { State } from '../core/state.js';
import { updateDatetimeBar } from '../ui/datetime.js';
import {setSunPosition, initial_sun_position} from "../scene/earth.js";
import {
    loadSatCSV, loadGSCSV, loadOrbitalCSV,
    loadSatcomCSV, loadInitialPositionCSV, loadEOCSV, loadZonesCSV,
} from '../loaders.js';
import {ecefToThreeJS} from "../core/utils.js";

export function initWebSocket() {
    const ws = new WebSocket('ws://localhost:8765');

    ws.onopen = () => console.log('[WS] Connected to Java server');

    ws.onmessage = (event) => {
        const msg = event.data;
        if      (msg.startsWith('EPOCH:'))       { State.set('obsEpoch', new Date(msg.slice(6)).getTime()); updateDatetimeBar(); }
        else if (msg.startsWith('GS_CSV:'))      loadGSCSV(msg.slice(7));
        else if (msg.startsWith('SAT_CSV:'))     loadSatCSV(msg.slice(8));
        else if (msg.startsWith('ORBITAL_CSV:')) loadOrbitalCSV(msg.slice(12));
        else if (msg.startsWith('SATCOM_CSV:'))  loadSatcomCSV(msg.slice(11));
        else if (msg.startsWith('EO_CSV:'))      loadEOCSV(msg.slice(7));
        else if (msg.startsWith('EO_Zones:'))    loadZonesCSV(msg.slice(9));
        else if (msg.startsWith('configurator')) loadInitialPositionCSV(msg.slice(12));
        else if (msg.startsWith('Sun position:')) {
            const [x, y, z] = msg.slice(13).split(',').map(Number);
            const [tx, ty, tz] = ecefToThreeJS([x, y, z]);
            setSunPosition(tx,ty,tz);
        }

    };

    ws.onerror = e  => console.error('[WS] Error:', e);
    ws.onclose = () => console.warn('[WS] Connection closed');

    return ws;
}