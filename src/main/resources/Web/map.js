/* ================================================================
 * map.js  –  D3 map initialisation and rendering
 *
 * Depends on: config.js, utils.js, state.js
 * ================================================================ */

// ── Initialisation ───────────────────────────────────────────────

function initMap() {
    const container = document.getElementById('mapDiv');
    W = container.clientWidth;
    H = Math.round(W * 0.45);

    svg        = d3.select('#mapDiv').append('svg').attr('width', W).attr('height', H);
    projection = d3.geoEquirectangular().scale(W / 6.28).translate([W / 2, H / 2]);
    pathGen    = d3.geoPath().projection(projection);

    // Background
    svg.append('rect').attr('width', W).attr('height', H).attr('fill', '#0d1b2a');

    // Graticule grid
    const graticule = d3.geoGraticule()();
    svg.append('g').append('path').datum(graticule)
        .attr('d', pathGen)
        .attr('fill', 'none')
        .attr('stroke', 'rgba(255,255,255,0.08)')
        .attr('stroke-width', 0.5);

    // Layer order matters — last appended is on top
    const landLayer = svg.append('g');
    eoLayer    = svg.append('g');
    trackLayer = svg.append('g');
    gsLayer    = svg.append('g');
    obsLayer   = svg.append('g');
    dotLayer   = svg.append('g');

    // Load world topology
    d3.json('https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json')
        .then(world => {
            landLayer.selectAll('path')
                .data(topojson.feature(world, world.objects.countries).features)
                .join('path')
                .attr('d', pathGen)
                .attr('fill', '#1a3a5c')
                .attr('stroke', 'rgba(255,255,255,0.15)')
                .attr('stroke-width', 0.4);
            window.mapInitialized = true;
        })
        .catch(() => { window.mapInitialized = true; });
}

/** Tear down and recreate the map (used on window resize). */
function resetmap() {
    d3.select('#mapDiv').selectAll('*').remove();
    initMap();
}

// ── Ground stations ──────────────────────────────────────────────

function drawGroundStations() {
    gsLayer.selectAll('*').remove();
    groundStations.forEach(gs => {
        const pos = projection([gs.lon, gs.lat]);
        if (!pos) return;
        const col  = gs.activated ? '#4caf50' : '#f44336';
        const size = 7;
        const diamond = `M${pos[0]},${pos[1] - size} L${pos[0] + size},${pos[1]} L${pos[0]},${pos[1] + size} L${pos[0] - size},${pos[1]} Z`;
        gsLayer.append('path')
            .attr('d', diamond)
            .attr('fill', col)
            .attr('stroke', '#fff')
            .attr('stroke-width', 0.8)
            .attr('opacity', 0.9);
        gsLayer.append('text')
            .attr('x', pos[0] + 10).attr('y', pos[1] + 4)
            .attr('fill', col).attr('font-size', '10px').attr('font-family', 'monospace')
            .text(gs.name);
    });
}

// ── EO observation zones ─────────────────────────────────────────

function drawEOAreas() {
    eoLayer.selectAll('*').remove();
    EOzones.forEach(area => {
        const projected = area.points.map(p => projection(p)).filter(Boolean);
        if (projected.length < 3) return;
        const polyPath = 'M' + projected.map(p => `${p[0]},${p[1]}`).join('L') + 'Z';
        eoLayer.append('path')
            .attr('d', polyPath)
            .attr('fill', '#29b6f6')
            .attr('fill-opacity', 0.15)
            .attr('stroke', '#29b6f6')
            .attr('stroke-width', 1.2)
            .attr('stroke-opacity', 0.7);
        const cx = projected.reduce((s, p) => s + p[0], 0) / projected.length;
        const cy = projected.reduce((s, p) => s + p[1], 0) / projected.length;
        eoLayer.append('text')
            .attr('x', cx).attr('y', cy)
            .attr('fill', '#29b6f6')
            .attr('font-size', '10px')
            .attr('font-family', 'monospace')
            .attr('text-anchor', 'middle')
            .text(area.name);
    });
}

// ── Satellite tracks ─────────────────────────────────────────────

function drawTracks() {
    trackLayer.selectAll('*').remove();
    Object.keys(sats).forEach((name, si) => {
        if (hiddenSats.has(name)) return;
        const col  = COLORS[si % COLORS.length];
        const pts  = sats[name];

        // Split track at antimeridian crossings to avoid long horizontal lines
        const chunks = [];
        let chunk = [pts[0]];
        for (let i = 1; i < pts.length; i++) {
            const prev = xyz2ll(pts[i - 1].x, pts[i - 1].y, pts[i - 1].z);
            const curr = xyz2ll(pts[i].x, pts[i].y, pts[i].z);
            if (Math.abs(curr[0] - prev[0]) > 180) { chunks.push(chunk); chunk = []; }
            chunk.push(pts[i]);
        }
        chunks.push(chunk);

        const lineGen = d3.line()
            .x(d => { const p = projection(xyz2ll(d.x, d.y, d.z)); return p ? p[0] : null; })
            .y(d => { const p = projection(xyz2ll(d.x, d.y, d.z)); return p ? p[1] : null; })
            .defined(d => projection(xyz2ll(d.x, d.y, d.z)) !== null);

        chunks.forEach(ch => {
            trackLayer.append('path').datum(ch)
                .attr('d', lineGen)
                .attr('fill', 'none')
                .attr('stroke', col)
                .attr('stroke-width', 1.2)
                .attr('opacity', 0.5);
        });
    });
}

// ── Active observation / SATCOM link lines ───────────────────────

function drawLinksLines(currentMs) {
    obsLayer.selectAll('*').remove();
    if (!satcom_links.length) return;
    satcom_links.forEach(obs => {
        if (currentMs < obs.startMs || currentMs > obs.endMs) return;
        if (hiddenSats.has(obs.satName) || !sats[obs.satName]) return;

        const best   = getBest(sats[obs.satName], times[idx]);
        const satLL  = xyz2ll(best.x, best.y, best.z);
        const satPos = projection(satLL);
        if (!satPos) return;

        const centroid = getZoneCentroid(obs.zone);
        if (!centroid) return;
        const zonePos = projection(centroid);
        if (!zonePos) return;

        obsLayer.append('line')
            .attr('x1', satPos[0]).attr('y1', satPos[1])
            .attr('x2', zonePos[0]).attr('y2', zonePos[1])
            .attr('stroke', '#ffffaa')
            .attr('stroke-width', 1.4)
            .attr('stroke-dasharray', '5,3')
            .attr('opacity', 0.75);
    });
}

// ── Satellite dots + labels ──────────────────────────────────────

function updateDots() {
    if (!times.length) return;
    const t         = times[idx];
    const currentMs = currentUnixMs();

    document.getElementById('tSlider').value = idx;

    dotLayer.selectAll('*').remove();

    Object.keys(sats).forEach((name, si) => {
        if (hiddenSats.has(name)) return;
        const col  = COLORS[si % COLORS.length];
        const best = getBest(sats[name], t);
        const ll   = xyz2ll(best.x, best.y, best.z);
        const pos  = projection(ll);
        if (!pos) return;

        const isObserving = !isNaN(currentMs) && observations.some(o =>
            o.satName === name && currentMs >= o.startMs && currentMs <= o.endMs);
        const dotColor = isObserving ? '#00e676' : col;

        // Firing ring
        if (best.firing) {
            dotLayer.append('circle')
                .attr('cx', pos[0]).attr('cy', pos[1]).attr('r', 11)
                .attr('fill', 'none').attr('stroke', '#ffb74d').attr('stroke-width', 2);
        }
        // Observation ring
        if (isObserving) {
            dotLayer.append('circle')
                .attr('cx', pos[0]).attr('cy', pos[1]).attr('r', 10)
                .attr('fill', 'none').attr('stroke', '#00e676').attr('stroke-width', 1.5)
                .attr('opacity', 0.4);
        }
        // Main dot
        dotLayer.append('circle')
            .attr('cx', pos[0]).attr('cy', pos[1]).attr('r', 6)
            .attr('fill', dotColor).attr('stroke', '#fff').attr('stroke-width', 0.5);
        // Name label
        dotLayer.append('text')
            .attr('x', pos[0] + 9).attr('y', pos[1] + 4)
            .attr('fill', '#ffffff').attr('font-size', '11px').attr('font-family', 'monospace')
            .text(name);
        // Altitude label
        const alt = (Math.sqrt(best.x ** 2 + best.y ** 2 + best.z ** 2) - EARTH_R) / 1000;
        dotLayer.append('text')
            .attr('x', pos[0] + 9).attr('y', pos[1] + 15)
            .attr('fill', 'rgba(255,255,255,0.5)').attr('font-size', '10px').attr('font-family', 'monospace')
            .text(Math.round(alt) + ' km');
    });

    drawLinksLines(currentMs);
    updateDatetimeBar();
    updateStats(t, currentMs);
    if (popupSatName) refreshSatInfoPopup();
}

// ── Helpers ──────────────────────────────────────────────────────

/** Return [lon, lat] centroid for a named zone (GS or EO). */
function getZoneCentroid(zoneName) {
    const gs = groundStations.find(g => g.name === zoneName);
    if (gs) return [gs.lon, gs.lat];
    const eo = EOzones.find(z => z.name === zoneName);
    return eo ? eo.centroid : null;
}