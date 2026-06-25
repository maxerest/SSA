// ================================================================
// ui/rightPanel.js — Satellite detail (right panel)
// ================================================================

import { EARTH_R, MAX_EVENTS_SHOWN } from '../core/config.js';
import { State }                      from '../core/state.js';
import { closestPoint, formatDeltaT } from '../core/utils.js';
import { getOrbitalAtTime, getNextDetection, getVelocityKms } from '../data/propagation.js';

export function selectSat(satName) {
    State.set('selectedSat', satName);

    document.querySelectorAll('.sat-card').forEach(el => el.classList.remove('selected'));
    document.getElementById(`satcard-${satName}`)?.classList.add('selected');

    const sat = State.sats[satName];
    document.getElementById('rightPanelHeader').classList.add('has-sat');
    document.getElementById('rightPanelDot').classList.add('visible');
    document.getElementById('rightPanelTitle').style.display = 'none';
    document.getElementById('rightPanelSatRow').classList.add('visible');
    document.getElementById('rightPanelSatDot').style.background = sat ? sat.colorHex : '#888';
    document.getElementById('rightPanelSatName').textContent = satName;
    document.getElementById('rightPanelClose').classList.add('visible');
    document.getElementById('rightPanelEmpty').style.display = 'none';
    document.getElementById('rightPanelContent').classList.add('visible');

    refreshRightPanel(satName);
}

export function deselectSat() {
    State.set('selectedSat', null);

    document.querySelectorAll('.sat-card').forEach(el => el.classList.remove('selected'));
    document.getElementById('rightPanelHeader').classList.remove('has-sat');
    document.getElementById('rightPanelDot').classList.remove('visible');
    document.getElementById('rightPanelTitle').style.display = '';
    document.getElementById('rightPanelSatRow').classList.remove('visible');
    document.getElementById('rightPanelClose').classList.remove('visible');
    document.getElementById('rightPanelEmpty').style.display = '';
    document.getElementById('rightPanelContent').classList.remove('visible');
}

export function refreshRightPanel(satName) {
    if (!satName || !State.sats[satName]) return;

    const sat     = State.sats[satName];
    const t       = State.times[State.idx] || 0;
    const closest = closestPoint(sat.pts, t);
    const r       = Math.sqrt(closest.x**2 + closest.y**2 + closest.z**2);
    const altKm   = ((r - EARTH_R) / 1000).toFixed(2);
    const orb     = getOrbitalAtTime(satName, t);
    const velKms  = getVelocityKms(satName, closest.t);

    const eoEvents   = sat.pts.filter(p => p.firing > 0);
    const nextEO     = getNextDetection(satName, 'eo');
    const nextSatcom = getNextDetection(satName, 'satcom');
    const links      = State.satcomLinks.filter(l => l.sat === satName);
    const totalData  = links.reduce((s, l) => s + l.dataMB, 0);
    const orbitStr   = orb ? (orb.e < 0.01 ? 'Circular' : orb.e < 0.1 ? 'Near-circular' : 'Elliptical') : null;

    document.getElementById('rightPanelContent').innerHTML = `
    <div class="detail-section">
        <div class="detail-section-title">Orbital Parameters</div>
        <div class="detail-row"><span class="detail-key">Altitude</span><span class="detail-val accent">${altKm} km</span></div>
        <div class="detail-row"><span class="detail-key">Velocity</span><span class="detail-val">${velKms} km/s</span></div>
        <div class="detail-row"><span class="detail-key">Sim time T</span><span class="detail-val">${closest.t.toFixed(0)} s</span></div>
        ${orb ? `
        <div class="detail-row"><span class="detail-key">Semi-major axis</span><span class="detail-val accent">${(orb.a/1000).toFixed(3)} km</span></div>
        <div class="detail-row"><span class="detail-key">Eccentricity</span><span class="detail-val">${orb.e.toFixed(6)}</span></div>
        <div class="detail-row"><span class="detail-key">Inclination</span><span class="detail-val">${(orb.i*180/Math.PI).toFixed(4)}°</span></div>
        <div class="detail-row"><span class="detail-key">Orbit type</span><span class="detail-val">${orbitStr}</span></div>
        ` : '<div class="detail-row"><span class="detail-key" style="font-style:italic;color:var(--text-dim)">No orbital data loaded</span></div>'}
    </div>

    <div class="detail-section">
        <div class="detail-section-title">Current Status</div>
        <div class="detail-row"><span class="detail-key">GS Detection</span>
            <span class="detail-val ${closest.detected ? 'good' : 'bad'}">${closest.detected ? '✓ Detected' : '✗ Not detected'}</span></div>
        <div class="detail-row"><span class="detail-key">EO Firing</span>
            <span class="detail-val ${closest.firing ? 'eo' : ''}">${closest.firing ? '🔥 Active' : 'Inactive'}</span></div>
        <div class="detail-row"><span class="detail-key">Active station</span>
            <span class="detail-val">${closest.station && closest.station !== 'nan' ? closest.station : '—'}</span></div>
        <div class="detail-row"><span class="detail-key">Sat type</span>
            <span class="detail-val">${sat.isNoisy ? 'Noisy' : 'Real'}</span></div>
    </div>

    <div class="detail-section">
        <div class="detail-section-title">Next Events</div>
        <div class="detail-row"><span class="detail-key">Next EO pass</span>
            <span class="detail-val ${nextEO ? 'eo' : ''}">${nextEO ? formatDeltaT(t, nextEO.t) : 'None remaining'}</span></div>
        <div class="detail-row"><span class="detail-key">Next downlink</span>
            <span class="detail-val ${nextSatcom ? 'accent' : ''}">${nextSatcom ? formatDeltaT(t, nextSatcom.t) : 'None remaining'}</span></div>
        ${nextSatcom?.station ? `<div class="detail-row"><span class="detail-key">Via station</span><span class="detail-val">${nextSatcom.station}</span></div>` : ''}
    </div>

    <div class="detail-section">
        <div class="detail-section-title">EO Detection Events (${eoEvents.length})</div>
        ${eoEvents.length === 0
        ? '<div class="no-events">No EO detections in simulation</div>'
        : `<div class="event-list">
                ${eoEvents.slice(0, MAX_EVENTS_SHOWN).map(p => `
                    <div class="event-item">
                        <div class="event-item-header">
                            <span class="event-name">T = ${p.t.toFixed(0)}s</span>
                            <span class="event-badge badge-eo">EO</span>
                        </div>
                    </div>`).join('')}
                ${eoEvents.length > MAX_EVENTS_SHOWN ? `<div style="font-size:10px;color:var(--text-dim);padding:4px 0;">+${eoEvents.length - MAX_EVENTS_SHOWN} more</div>` : ''}
               </div>`}
    </div>

    <div class="detail-section">
        <div class="detail-section-title">GS Downlink Events (${links.length})</div>
        ${links.length === 0
        ? '<div class="no-events">No satcom links in simulation</div>'
        : `<div class="detail-row" style="margin-bottom:6px;">
                   <span class="detail-key">Total data</span>
                   <span class="detail-val accent">${totalData.toFixed(1)} MB</span>
               </div>
               <div class="event-list">
               ${links.map(l => {
            const startISO = l.start.substring(0,19).replace('T',' ');
            const endISO   = l.end.substring(0,19).replace('T',' ');
            const durMin   = (l.duration / 60).toFixed(1);
            return `<div class="event-item">
                       <div class="event-item-header">
                           <span class="event-name">${l.gs}</span>
                           <span class="event-badge badge-satcom">SATCOM</span>
                       </div>
                       <div class="event-time">${startISO} UTC</div>
                       <div class="event-time">→ ${endISO} UTC (${durMin} min)</div>
                       <div class="event-time" style="color:var(--teal);margin-top:2px;">${l.dataMB.toFixed(1)} MB</div>
                   </div>`;
        }).join('')}
               </div>`}
    </div>`;
}