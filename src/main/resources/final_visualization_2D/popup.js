/* ================================================================
 * popup.js  –  Satellite info popup
 *
 * Depends on: utils.js, state.js
 * ================================================================ */

/**
 * Open the info popup for a satellite, positioned near its stat card.
 * @param {string}  satName  - satellite identifier
 * @param {string}  satColor - hex color for the dot indicator
 * @param {Element} cardEl   - the stat card DOM element (used for positioning)
 */
function openSatInfoPopup(satName, satColor, cardEl) {
    popupSatName = satName;
    document.getElementById('satInfoName').textContent = satName;
    document.getElementById('satInfoDot').style.background = satColor;
    refreshSatInfoPopup();

    const popup = document.getElementById('satInfoPopup');
    popup.classList.remove('hidden');

    // Position below the card, clamped to viewport
    const rect = cardEl.getBoundingClientRect();
    const PW = 320, PH = 420;
    let left = rect.left;
    let top  = rect.bottom + 6;
    if (left + PW > window.innerWidth  - 8) left = window.innerWidth  - PW - 8;
    if (top  + PH > window.innerHeight - 8) top  = rect.top - PH - 6;
    if (top < 8) top = 8;
    popup.style.left = left + 'px';
    popup.style.top  = top  + 'px';
}

/** Close the popup and clear popup state. */
function closeSatInfoPopup() {
    popupSatName = null;
    document.getElementById('satInfoPopup').classList.add('hidden');
}

/**
 * Re-render the popup body for the currently open satellite.
 * Called whenever time advances so active/upcoming/past status stays current.
 */
function refreshSatInfoPopup() {
    if (!popupSatName) return;
    const currentMs = currentUnixMs();
    const body = document.getElementById('satInfoPopupBody');
    body.innerHTML = '';

    _renderWindowSection(body, '📡 EO Observations',
        observations.filter(o => o.satName === popupSatName),
        'No EO observation windows', currentMs, false);

    _renderWindowSection(body, '🔗 SATCOM Links',
        satcom_links.filter(l => l.satName === popupSatName),
        'No SATCOM link windows', currentMs, true);
}

// ── Private helpers ──────────────────────────────────────────────

/**
 * Render a titled section of time-window entries into the popup body.
 * @param {Element}  body       - popup body container
 * @param {string}   title      - section heading text
 * @param {Array}    windows    - filtered observation/link entries
 * @param {string}   emptyMsg   - message when windows is empty
 * @param {number}   currentMs  - current simulation time (Unix-ms)
 * @param {boolean}  isSatcom   - true → use SATCOM styling
 */
function _renderWindowSection(body, title, windows, emptyMsg, currentMs, isSatcom) {
    const titleEl = document.createElement('div');
    titleEl.className = 'info-section-title';
    titleEl.textContent = title;
    body.appendChild(titleEl);

    if (windows.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'info-empty';
        empty.textContent = emptyMsg;
        body.appendChild(empty);
        return;
    }

    const now = isNaN(currentMs) ? 0 : currentMs;
    const statusOrder = o =>
        now >= o.startMs && now <= o.endMs ? 0 :
            now < o.startMs                    ? 1 : 2;

    const sorted = [...windows].sort(
        (a, b) => statusOrder(a) - statusOrder(b) || a.startMs - b.startMs
    );

    sorted.forEach(win => {
        const isActive   = !isNaN(currentMs) && currentMs >= win.startMs && currentMs <= win.endMs;
        const isUpcoming = !isNaN(currentMs) && currentMs < win.startMs;
        const statusText = isActive   ? (isSatcom ? '● LINKED NOW' : '● ACTIVE NOW')
            : isUpcoming ? '◌ Upcoming'
                : '○ Past';
        const statusCls  = isActive   ? 'obs-status-active'
            : isUpcoming ? 'obs-status-upcoming'
                : 'obs-status-past';
        const duration   = msToDuration(win.endMs - win.startMs);

        const entry = document.createElement('div');
        entry.className = 'obs-entry' + (isSatcom ? ' satcom' : '');
        entry.innerHTML = `
            <div class="obs-zone">${win.zone}</div>
            <div class="obs-time">Start: ${formatMs(win.startMs)}</div>
            <div class="obs-time">End:   ${formatMs(win.endMs)} &nbsp;·&nbsp; ${duration}</div>
            <div class="${statusCls}">${statusText}</div>`;
        body.appendChild(entry);
    });
}

// ── Event wiring ─────────────────────────────────────────────────

document.getElementById('satInfoPopupClose').addEventListener('click', closeSatInfoPopup);

// Close on click outside the popup (but not on info-btn — that's handled in stats.js)
document.addEventListener('click', e => {
    const popup = document.getElementById('satInfoPopup');
    if (!popup.classList.contains('hidden') &&
        !popup.contains(e.target) &&
        !e.target.classList.contains('info-btn')) {
        closeSatInfoPopup();
    }
});