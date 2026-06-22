// ================================================================
// core/state.js — Centralized reactive state store
// ================================================================

/**
 * All mutable application state lives here.
 * Components read from State directly and subscribe to changes
 * via State.subscribe(key, fn) — they never hold their own copies.
 *
 * Usage:
 *   State.set('playing', true);
 *   State.subscribe('playing', val => playBtn.textContent = val ? '⏸ Pause' : '▶ Play');
 */

const _listeners = new Map();

const _state = {
    /** @type {Record<string, SatEntry>} */
    sats: {},

    /** @type {Record<string, OrbitalEntry[]>} */
    orbitalParams: {},

    /** @type {number[]} Sorted array of simulation timestamps (seconds) */
    times: [],

    /** @type {number} Current index into `times` */
    idx: 0,

    /** @type {number|null} Unix-ms of simulation T=0 */
    obsEpoch: null,

    /** @type {GSEntry[]} */
    groundStations: [],

    /** @type {boolean} */
    playing: false,

    /** @type {number} Playback speed multiplier (can be fractional) */
    speedMultiplier: 1,

    /** @type {boolean} */
    showTrails: true,

    /** @type {boolean} */
    showCones: false,

    /** @type {string|null} Currently selected satellite name */
    selectedSat: null,

    /** @type {SatcomLink[]} */
    satcomLinks: [],
};

export const State = {
    // ── Read ──────────────────────────────────────────────────────
    get(key) {
        return _state[key];
    },

    // ── Write + notify ────────────────────────────────────────────
    set(key, value) {
        _state[key] = value;
        const fns = _listeners.get(key);
        if (fns) fns.forEach(fn => fn(value));
    },

    // ── Convenience mutators ──────────────────────────────────────
    /** Merge an object into a dict-valued key (e.g. sats, orbitalParams) */
    merge(key, patch) {
        _state[key] = { ..._state[key], ...patch };
        const fns = _listeners.get(key);
        if (fns) fns.forEach(fn => fn(_state[key]));
    },

    /** Replace the value at key with an empty object/array */
    reset(key) {
        const empty = Array.isArray(_state[key]) ? [] : {};
        this.set(key, empty);
    },

    // ── Subscription ──────────────────────────────────────────────
    /**
     * @param {string}   key
     * @param {Function} fn  Called with new value on every set()
     * @returns {Function}   Unsubscribe function
     */
    subscribe(key, fn) {
        if (!_listeners.has(key)) _listeners.set(key, []);
        _listeners.get(key).push(fn);
        return () => {
            const arr = _listeners.get(key);
            const i   = arr.indexOf(fn);
            if (i !== -1) arr.splice(i, 1);
        };
    },

    // ── Direct property access (for hot-path reads in render loop) ─
    // Getters so callers can do State.sats, State.times, etc.
    get sats()           { return _state.sats; },
    get orbitalParams()  { return _state.orbitalParams; },
    get times()          { return _state.times; },
    get idx()            { return _state.idx; },
    get obsEpoch()       { return _state.obsEpoch; },
    get groundStations() { return _state.groundStations; },
    get playing()        { return _state.playing; },
    get speedMultiplier(){ return _state.speedMultiplier; },
    get showTrails()     { return _state.showTrails; },
    get showCones()      { return _state.showCones; },
    get selectedSat()    { return _state.selectedSat; },
    get satcomLinks()    { return _state.satcomLinks; },
};