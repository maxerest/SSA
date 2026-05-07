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
        const col    = COLORS[si % COLORS.length];
        const best   = getBest(sats[name], t);
        const ll     = xyz2ll(best.x, best.y, best.z);
        const alt    = (Math.sqrt(best.x ** 2 + best.y ** 2 + best.z ** 2) - EARTH_R) / 1000;
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
            buildLegend(EOzones.length > 0);
        });

        row.appendChild(card);
    });
}

/**
 * Rebuild the map legend below the map.
 * @param {boolean} hasEO - whether EO zones are loaded
 */
function buildLegend(hasEO) {
    const leg = document.getElementById('legend');
    leg.innerHTML = '';

    Object.keys(sats).forEach((name, si) => {
        const col    = COLORS[si % COLORS.length];
        const hidden = hiddenSats.has(name);
        leg.innerHTML += `<span class="legend-item" style="opacity:${hidden ? 0.35 : 1}">` +
            `<span class="legend-dot" style="background:${col};"></span>${name}</span>`;
    });

    if (groundStations.length > 0) {
        leg.innerHTML += `<span class="legend-item"><span style="display:inline-block;width:10px;height:10px;background:#4caf50;clip-path:polygon(50% 0%,100% 50%,50% 100%,0% 50%);"></span>GS active</span>`;
        leg.innerHTML += `<span class="legend-item"><span style="display:inline-block;width:10px;height:10px;background:#f44336;clip-path:polygon(50% 0%,100% 50%,50% 100%,0% 50%);"></span>GS inactive</span>`;
    }
    if (hasEO) {
        leg.innerHTML += `<span class="legend-item"><span style="display:inline-block;width:10px;height:10px;background:#1a2a3a;opacity:0.5;border:1px solid #15b1f9;"></span>EO observation zone</span>`;
    }
    if (observations.length > 0) {
        leg.innerHTML += `<span class="legend-item"><span style="display:inline-block;width:18px;height:2px;background:#ffffaa;opacity:0.8;border-top:2px dashed #ffffaa;margin-bottom:3px;"></span>Active observation</span>`;
    }
}