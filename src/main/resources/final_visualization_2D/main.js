/* ================================================================
 * main.js  –  Application entry point
 *
 * Responsibilities:
 *   - Wire parsed data into shared state and trigger re-renders
 *   - Attach all UI event listeners
 *   - Expose window.* entry points for the Java bridge
 *   - Manage the playback timer
 *   - Kick off map initialisation
 * ================================================================ */

// ── Datetime bar ─────────────────────────────────────────────────

function updateDatetimeBar() {
    const bar       = document.getElementById('datetimeBar');
    const currentMs = currentUnixMs();

    if (obsEpoch === null) {
        bar.innerHTML = '<span class="no-epoch">No epoch set — load an observations or SATCOM CSV to display simulation date</span>';
        return;
    }
    if (isNaN(currentMs)) {
        bar.innerHTML = '<span class="no-epoch">Epoch set — load satellite CSV to begin</span>';
        return;
    }

    const iso       = new Date(currentMs).toISOString();
    const datePart  = iso.substring(0, 10);
    const timePart  = iso.substring(11, 19);
    const elapsedS  = Math.round((currentMs - obsEpoch) / 1000);
    const elH       = Math.floor(elapsedS / 3600);
    const elM       = Math.floor((elapsedS % 3600) / 60);
    const elS       = elapsedS % 60;
    const elapsedStr = `T+${elH}h ${String(elM).padStart(2, '0')}m ${String(elS).padStart(2, '0')}s`;

    bar.innerHTML =
        `<span class="epoch-label">SIM DATE</span>` +
        `<span class="epoch-val">${datePart} &nbsp;${timePart} UTC</span>` +
        `<span class="epoch-label" style="margin-left:16px;">ELAPSED</span>` +
        `<span class="epoch-val">${elapsedStr}</span>`;
}

// ── Data application helpers ─────────────────────────────────────

/** Apply parsed satellite data and refresh the full UI. */
function applyData(parsed) {
    sats  = parsed.sats;
    times = parsed.times;
    idx   = 0;
    hiddenSats.clear();

    const sl = document.getElementById('tSlider');
    sl.min = 0; sl.max = times.length - 1; sl.value = 0;

    document.getElementById('status').textContent =
        `${Object.keys(sats).length} satellite(s), ${times.length} time steps loaded.` +
        (observations.length ? ` | ${observations.length} observation window(s) loaded.` : '');

    buildLegend(EOzones.length > 0);
    drawTracks();
    updateDots();
}

function applyGSData(stations) {
    groundStations = stations;
    drawGroundStations();
    buildLegend(EOzones.length > 0);
}

function applyEOData(zones) {
    EOzones = zones;
    drawEOAreas();
}

function applyObsData(parsed) {
    observations = parsed;
    const status = document.getElementById('status');
    status.textContent = status.textContent.replace(/ \| \d+ obs.*/, '') +
        ` | ${observations.length} observation window(s) loaded.`;
    updateDatetimeBar();
    updateDots();
    buildLegend(EOzones.length > 0);
}

function applySATCOMData(parsed) {
    satcom_links = parsed;
    const status = document.getElementById('status');
    status.textContent = status.textContent.replace(/ \| \d+ links.*/, '') +
        ` | ${satcom_links.length} links loaded.`;
    updateDatetimeBar();
    updateDots();
}

// ── Text-based loaders (used by file inputs and Java bridge) ─────

function loadGSFromText(text)     { applyGSData(parseGSCSV(text)); }
function loadEOFromText(text)     { applyEOData(parseEOCSV(text)); }
function loadObsFromText(text)    { applyObsData(parseObsCSV(text)); }
function loadSATCOMFromText(text) { applySATCOMData(parseSATCOMcsv(text)); }

// ── Playback timer ───────────────────────────────────────────────

function restartTimer() {
    if (!playing) return;
    clearInterval(timer);
    const interval = Math.max(16, Math.round(120 / speedMultiplier));
    const steps    = speedMultiplier >= 1 ? Math.round(speedMultiplier) : 1;
    timer = setInterval(() => {
        idx = (idx + steps) % times.length;
        updateDots();
    }, interval);
}

// ── UI event listeners ───────────────────────────────────────────

document.getElementById('tSlider').addEventListener('input', e => {
    idx = parseInt(e.target.value);
    updateDots();
});

document.getElementById('speedSlider').addEventListener('input', e => {
    const v = parseInt(e.target.value);
    speedMultiplier = sliderToSpeed(v);
    const label = speedMultiplier < 1
        ? speedMultiplier.toFixed(1) + 'x'
        : Math.round(speedMultiplier) + 'x';
    document.getElementById('speedLabel').textContent = label;
    restartTimer();
});

document.getElementById('playBtn').addEventListener('click', () => {
    playing = !playing;
    document.getElementById('playBtn').textContent = playing ? 'Pause' : 'Play';
    if (playing) restartTimer();
    else         clearInterval(timer);
});

// File-upload buttons
const fileBindings = [
    { btn: 'uploadBtn',    input: 'csvFile',  handler: text => applyData(parseCSV(text)) },
    { btn: 'uploadGSBtn',  input: 'gsFile',   handler: loadGSFromText },
    { btn: 'uploadEOBtn',  input: 'eoFile',   handler: loadEOFromText },
    { btn: 'uploadObsBtn', input: 'obsFile',  handler: loadObsFromText },
];
fileBindings.forEach(({ btn, input, handler }) => {
    document.getElementById(btn).addEventListener('click', () =>
        document.getElementById(input).click()
    );
    document.getElementById(input).addEventListener('change', e => {
        const f = e.target.files[0];
        if (!f) return;
        const r = new FileReader();
        r.onload = ev => handler(ev.target.result);
        r.readAsText(f);
    });
});

// Resize handler
window.addEventListener('resize', () => {
    const wait = setInterval(() => {
        if (!window.mapInitialized) return;
        clearInterval(wait);
        resetmap();
        drawTracks();
        drawEOAreas();
        drawGroundStations();
        updateDots();
    }, 50);
});

// ── Java bridge entry points ─────────────────────────────────────
// These are called by SatelliteTrackerUI.java via engine.executeScript(…)

window.receiveCSVContent       = text => applyData(parseCSV(text));
window.receiveObsCSVContent    = text => loadObsFromText(text);
window.receiveEOCSVContent     = text => loadEOFromText(text);
window.receiveSATCOMCSVContent = text => loadSATCOMFromText(text);

/**
 * Called from Java to set the simulation epoch.
 * @param {string} isoDateString - ISO-8601 date string (e.g. "2026-04-23T14:00:00Z")
 */
window.setSimulationEpoch = function(isoDateString) {
    obsEpoch = new Date(isoDateString).getTime();
    updateDatetimeBar();
};

// ── Bootstrap ────────────────────────────────────────────────────

window.mapInitialized = false;
initMap();