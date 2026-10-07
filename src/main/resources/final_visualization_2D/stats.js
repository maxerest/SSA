/* ================================================================
 * stats.js  –  Satellite stat cards and map legend
 *
 * Depends on: config.js, utils.js, state.js, map.js, popup.js
 * ================================================================ */

/**
 * Re-render all satellite stat cards for the given time step.
 * @param {number} t         - current simulation time (seconds)
 * @param {number} currentMs - current simulation time (Unix-ms)
 */
function updateStats(t, currentMs) {
    if (currentMs === undefined) currentMs = currentUnixMs();
    const row = document.getElementById('statsRow');
    row.innerHTML = '';

    Object.keys(sats).forEach((name, si) => {
        let col    = COLORS[si % COLORS.length]; // if you want different colors
        col = '#4fc3f7';
        const best   = getBest(sats[name], t);
        const ll     = xyz2ll(best.x, best.y, best.z);
        const alt    = (Math.sqrt(best.x ** 2 + best.y ** 2 + best.z ** 2) - EARTH_R) / 1000;
        const altitudeM = Math.sqrt(best.x ** 2 + best.y ** 2 + best.z ** 2) - EARTH_R;
        const footprint = groundFootprintRadius(altitudeM, ELEVATION);

        const hidden = hiddenSats.has(name);

        const hasActiveEO   = !isNaN(currentMs) && observations.some(o =>
            o.satName === name && currentMs >= o.startMs && currentMs <= o.endMs);
        const hasActiveLink = !isNaN(currentMs) && satcom_links.some(l =>
            l.satName === name && currentMs >= l.startMs && currentMs <= l.endMs);
        const hasAnyData    = observations.some(o => o.satName === name) ||
            satcom_links.some(l => l.satName === name);
        const hasActivity   = hasActiveEO || hasActiveLink;

        const card = document.createElement('div');
        card.className = 'stat-card' + (hidden ? ' hidden-sat' : '');
        card.title = hidden ? `Click to show ${name}` : `Click to hide ${name}`;
        card.innerHTML = `
            <div class="sat-name">
                <span class="dot" style="background:${col};"></span>
                ${name}
                <span class="eye-icon">${hidden ? 'show' : 'hide'}</span>
            </div>
            <div class="row">Lat <span class="val">${ll[1].toFixed(2)}°</span> &nbsp;Lon <span class="val">${ll[0].toFixed(2)}°</span></div>
            <div class="row">Alt <span class="val">${Math.round(alt)} km</span></div>`;

        // Info button — only when there is EO or SATCOM data loaded
        if (hasAnyData || observations.length > 0 || satcom_links.length > 0) {
            const btn = document.createElement('button');
            btn.className = 'info-btn' + (hasActivity ? ' has-activity' : '');
            btn.title     = `Show EO & SATCOM info for ${name}`;
            btn.textContent = 'i';
            btn.addEventListener('click', e => {
                e.stopPropagation();
                const popup = document.getElementById('satInfoPopup');
                if (popupSatName === name && !popup.classList.contains('hidden')) {
                    closeSatInfoPopup();
                } else {
                    openSatInfoPopup(name, col, card);
                }
            });
            card.appendChild(btn);
        }

        // Toggle satellite visibility on card click
        card.addEventListener('click', () => {
            if (hiddenSats.has(name)) hiddenSats.delete(name);
            else hiddenSats.add(name);
            drawTracks();
            updateDots();
        });

        row.appendChild(card);
    });
}


function groundFootprintRadius(altitudeM, elevationDeg) {
    const R = EARTH_R;
    const r = R + altitudeM;

    const elevation = elevationDeg * Math.PI / 180;

    const psi =
        Math.acos((R / r) * Math.cos(elevation))
        - elevation;

    return {
        centralAngleRad: psi,
        groundRadiusM: R * psi
    };
}