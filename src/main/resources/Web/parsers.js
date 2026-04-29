/* ================================================================
 * parsers.js  –  CSV text → structured data objects
 *
 * All functions are pure: they receive a string and return data.
 * They never touch the DOM or shared state.
 * ================================================================ */

/**
 * Parse the main satellite CSV.
 * Expected columns: name_sat, x, y, z, t, firing
 * Returns { sats: {name: [{x,y,z,t,firing}]}, times: [sorted t values] }
 */
function parseCSV(text) {
    const lines = text.trim().split('\n');
    const h = lines[0].split(',').map(s => s.trim());
    const iN = h.indexOf('name_sat');
    const iX = h.indexOf('x');
    const iY = h.indexOf('y');
    const iZ = h.indexOf('z');
    const iT = h.indexOf('t');
    const iF = h.indexOf('firing');

    const out = {}, ts = new Set();
    for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue;
        const c      = lines[i].split(',');
        const name   = c[iN]?.trim();
        const x      = parseFloat(c[iX]);
        const y      = parseFloat(c[iY]);
        const z      = parseFloat(c[iZ]);
        const t      = parseFloat(c[iT]);
        const firing = parseInt(c[iF]) || 0;
        if (!name || isNaN(x) || isNaN(t)) continue;
        if (!out[name]) out[name] = [];
        out[name].push({ x, y, z, t, firing });
        ts.add(t);
    }
    return { sats: out, times: Array.from(ts).sort((a, b) => a - b) };
}

/**
 * Parse a ground-station CSV.
 * Expected columns: name, lat, long, alt, activated
 * Returns [{name, lat, lon, activated}, …]
 */
function parseGSCSV(text) {
    const lines = text.trim().split('\n');
    const h = lines[0].split(',').map(s => s.trim());
    const iN   = h.indexOf('name');
    const iLat = h.indexOf('lat');
    const iLon = h.indexOf('long');
    const iA   = h.indexOf('activated');

    const out = [];
    for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue;
        const c         = lines[i].split(',');
        const name      = c[iN]?.trim();
        const lat       = parseFloat(c[iLat]);
        const lon       = parseFloat(c[iLon]);
        const activated = parseInt(c[iA]) === 1;
        if (!name || isNaN(lat) || isNaN(lon)) continue;
        out.push({ name, lat, lon, activated });
    }
    return out;
}

/**
 * Parse an EO observation-area CSV.
 * Format: name, lat1, lon1, alt1, lat2, lon2, alt2, …
 * Returns [{name, points: [[lon,lat],…], centroid: [lon,lat]}, …]
 */
function parseEOCSV(text) {
    const lines = text.trim().split('\n');
    const out = [];
    for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue;
        const c      = lines[i].split(',');
        const name   = c[0]?.trim();
        const points = [];
        for (let j = 1; j + 2 <= c.length; j += 3) {
            const lat    = parseFloat(c[j]);
            const lon    = parseFloat(c[j + 1]);
            if (!isNaN(lat) && !isNaN(lon)) {
                points.push([((lon + 180) % 360) - 180, lat]);  // normalise lon
            }
        }
        if (name && points.length >= 3) {
            const cx = points.reduce((s, p) => s + p[0], 0) / points.length;
            const cy = points.reduce((s, p) => s + p[1], 0) / points.length;
            out.push({ name, points, centroid: [cx, cy] });
        }
    }
    return out;
}

/**
 * Parse an EO observations schedule CSV.
 * Expected columns: zone_name, start_time, end_time, name_sat_doing_observation
 * Returns [{zone, satName, startMs, endMs}, …]
 */
function parseObsCSV(text) {
    return _parseWindowCSV(text, 'zone_name');
}

/**
 * Parse a SATCOM link schedule CSV.
 * Expected columns: GS_name, start_time, end_time, name_sat_doing_observation
 * Returns [{zone, satName, startMs, endMs}, …]
 */
function parseSATCOMcsv(text) {
    return _parseWindowCSV(text, 'GS_name');
}

/**
 * Shared parser for any "time-window per zone" CSV.
 * @param {string} text      - raw CSV text
 * @param {string} zoneCol   - name of the column holding the zone/GS name
 */
function _parseWindowCSV(text, zoneCol) {
    const lines = text.trim().split('\n');
    const h = lines[0].split(',').map(s => s.trim());
    const iZone  = h.indexOf(zoneCol);
    const iStart = h.indexOf('start_time');
    const iEnd   = h.indexOf('end_time');
    const iSat   = h.indexOf('name_sat_doing_observation');
    if (iZone < 0 || iStart < 0 || iEnd < 0 || iSat < 0) {
        console.warn(`CSV parser: missing columns in "${zoneCol}" CSV`, h);
        return [];
    }
    const out = [];
    for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue;
        const c       = lines[i].split(',');
        const zone    = c[iZone]?.trim();
        const satName = c[iSat]?.trim();
        const startMs = isoToMs(c[iStart]?.trim());
        const endMs   = isoToMs(c[iEnd]?.trim());
        if (!zone || !satName || isNaN(startMs) || isNaN(endMs)) continue;
        out.push({ zone, satName, startMs, endMs });
    }
    return out;
}