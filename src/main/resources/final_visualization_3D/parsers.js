// ================================================================
// data/parsers.js — CSV → typed domain objects
// ================================================================
//
// Every function here is pure: input text → output objects (or null).
// No DOM access, no state mutation, no Three.js.
// Errors are returned as { error: string } so callers can surface them.

import { SAT_COLORS } from '../core/config.js';

// ── Low-level CSV tokeniser ───────────────────────────────────────

/**
 * @param {string} text
 * @returns {{ headers: string[], rows: Record<string,string>[] } | { error: string }}
 */
export function parseCSV(text) {
    try {
        const lines = text.trim().split('\n');
        if (lines.length < 2) return { error: 'CSV has no data rows' };

        const headers = lines[0].split(',').map(h => h.trim().toLowerCase());

        const rows = [];
        for (let i = 1; i < lines.length; i++) {
            const line = lines[i].trim();
            if (!line) continue;
            const vals = line.split(',');
            const obj  = {};
            headers.forEach((h, j) => { obj[h] = vals[j] ? vals[j].trim() : ''; });
            rows.push(obj);
        }

        return { headers, rows };
    } catch (err) {
        return { error: `Parse failed: ${err.message}` };
    }
}

// ── Domain parsers ────────────────────────────────────────────────

/**
 * Parse a satellite position CSV (multi-time-step format).
 * @param {string} text
 * @returns {{ sats: Record<string,SatEntry>, times: number[], epoch: number } | { error: string }}
 */
export function parseSatCSV(text) {
    const result = parseCSV(text);
    if (result.error) return result;
    const { rows } = result;
    if (!rows.length) return { error: 'No rows in satellite CSV' };

    const grouped = {};
    rows.forEach(r => {
        const name = r['name_sat'] || r['name'] || 'Unknown';
        if (!grouped[name]) grouped[name] = [];
        grouped[name].push(r);
    });

    const timeSet = new Set();
    const sats    = {};

    Object.keys(grouped).forEach((satName, colorIdx) => {
        const colorHex = SAT_COLORS[colorIdx % SAT_COLORS.length];
        const pts = grouped[satName].map(r => {
            const t = parseFloat(r['t'] || 0);
            timeSet.add(t);
            const detStr = (r['detected_by_gs'] || '').toLowerCase();
            return {
                x:        parseFloat(r['x']),
                y:        parseFloat(r['y']),
                z:        parseFloat(r['z']),
                t,
                firing:   parseFloat(r['firing'] || 0),
                detected: detStr === 'true' || detStr === '1' ? 1 : 0,
                station:  r['nom_station'] || '',
            };
        }).filter(p => !isNaN(p.x) && !isNaN(p.y) && !isNaN(p.z));

        if (pts.length) {
            sats[satName] = {
                pts,
                colorHex,
                isNoisy: satName.toLowerCase().includes('noisy'),
            };
        }
    });

    const times = [...timeSet].sort((a, b) => a - b);
    const t0    = parseFloat(rows[0]['t'] || 0);
    const epoch = Date.now() - t0 * 1000;

    return { sats, times, epoch };
}

/**
 * Parse a static initial-position CSV (single point per satellite).
 * @param {string} text
 * @returns {{ sats: Record<string,SatEntry> } | { error: string }}
 */
export function parseInitialPositionCSV(text) {
    const result = parseCSV(text);
    if (result.error) return result;
    const { rows } = result;

    const sats = {};
    rows.forEach((r, colorIdx) => {
        const satName  = r['name_sat'] || r['name'] || `SAT_${colorIdx}`;
        const colorHex = SAT_COLORS[colorIdx % SAT_COLORS.length];
        const x = parseFloat(r['x']);
        const y = parseFloat(r['y']);
        const z = parseFloat(r['z']);
        if (isNaN(x) || isNaN(y) || isNaN(z)) return;

        sats[satName] = {
            pts: [{ x, y, z, t: 0, firing: 0, detected: 0, station: '' }],
            colorHex,
            isNoisy: satName.toLowerCase().includes('noisy'),
            isStatic: true,
        };
    });

    if (!Object.keys(sats).length) return { error: 'No valid satellite rows found' };
    return { sats };
}

/**
 * Parse a ground-station CSV.
 * @param {string} text
 * @returns {{ stations: GSEntry[] } | { error: string }}
 */
export function parseGSCSV(text) {
    const result = parseCSV(text);
    if (result.error) return result;
    const { rows } = result;

    const stations = rows.map((r, i) => ({
        name:      r['name'] || `GS_${i}`,
        lat:       parseFloat(r['lat'] || 0),
        lon:       parseFloat(r['long'] || r['lon'] || 0),
        alt:       parseFloat(r['alt'] || 0),          // km
        activated: (r['activated'] || 'true').toLowerCase() !== 'false',
    })).filter(s => !isNaN(s.lat) && !isNaN(s.lon));

    return { stations };
}

/**
 * Parse an orbital-parameters CSV.
 * @param {string} text
 * @returns {{ orbitalParams: Record<string, OrbitalEntry[]> } | { error: string }}
 */
export function parseOrbitalCSV(text) {
    const result = parseCSV(text);
    if (result.error) return result;
    const { rows } = result;

    const orbitalParams = {};
    rows.forEach(r => {
        const name = r['name_sat'] || r['name'] || 'Unknown';
        if (!orbitalParams[name]) orbitalParams[name] = [];
        orbitalParams[name].push({
            t:        parseFloat(r['t']       || 0),
            a:        parseFloat(r['a']       || 0),
            e:        parseFloat(r['e']       || 0),
            i:        parseFloat(r['i']       || 0),
            firing:   parseFloat(r['firing']  || 0),
            detected: (r['detected_by_gs'] || '').toLowerCase(),
            station:  r['nom_station'] || '',
        });
    });

    return { orbitalParams };
}

/**
 * Parse a satcom-links CSV.
 * @param {string} text
 * @returns {{ links: SatcomLink[] } | { error: string }}
 */
export function parseSatcomCSV(text) {
    const result = parseCSV(text);
    if (result.error) return result;
    const { rows } = result;

    const links = rows.map(r => ({
        gs:       r['gs_name'] || r['gs name'] || '',
        start:    r['start_time']  || '',
        end:      r['end_time']    || '',
        duration: parseFloat(r['duration_s'] || 0),
        sat:      r['name_sat_doing_observation'] || '',
        dataMB:   parseFloat(
            r['total_data_downlinkable(mb)'] ||
            r['total_data_downlinkable(MB)'] || 0
        ),
    }));

    return { links };
}