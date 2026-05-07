/* ================================================================
 * state.js  –  All shared mutable state
 *
 * Every other module reads/writes these variables.
 * Keeping them here makes data-flow explicit and easy to debug.
 * ================================================================ */

// ── Satellite data ───────────────────────────────────────────────
let sats       = {};   // { satName: [{x,y,z,t,firing}, …] }
let times      = [];   // sorted array of t values
let idx        = 0;    // current time-step index
let hiddenSats = new Set();

// ── Ancillary data ───────────────────────────────────────────────
let groundStations = [];   // [{name, lat, lon, activated}, …]
let EOzones        = [];   // [{name, points, centroid}, …]
let observations   = [];   // [{zone, satName, startMs, endMs}, …]  (EO)
let satcom_links   = [];   // [{zone, satName, startMs, endMs}, …]  (SATCOM)

// ── Simulation clock ─────────────────────────────────────────────
let obsEpoch = null;   // Unix-ms of t=0  (set from Java / Obs CSV)

/**
 * Return the current simulation time as Unix-ms,
 * or NaN if the epoch or time array are not yet set.
 */
function currentUnixMs() {
    if (obsEpoch === null || !times.length) return NaN;
    return obsEpoch + times[idx] * 1000;
}

// ── Playback ─────────────────────────────────────────────────────
let playing         = false;
let timer           = null;
let speedMultiplier = 1.0;

// ── D3 / map handles ─────────────────────────────────────────────
let svg, projection, pathGen, W, H;
let dotLayer, trackLayer, gsLayer, eoLayer, obsLayer;

// ── Info-popup state ─────────────────────────────────────────────
let popupSatName = null;

// ── Explorer state ───────────────────────────────────────────────
let explorerSelected = null;
let explorerData     = [];