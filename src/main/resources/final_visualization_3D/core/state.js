// ================================================================
// core/state.js — Centralized reactive state store
// ================================================================

const _listeners = new Map();

const _state = {
    sats: {},
    orbitalParams: {},
    times: [],
    idx: 0,
    obsEpoch: null,
    groundStations: [],
    playing: false,
    speedMultiplier: 1,
    showTrails: true,
    showCones: false,
    selectedSat: null,
    satcomLinks: [],
};

export const State = {
    get(key)        { return _state[key]; },

    set(key, value) {
        _state[key] = value;
        const fns = _listeners.get(key);
        if (fns) fns.forEach(fn => fn(value));
    },

    merge(key, patch) {
        _state[key] = { ..._state[key], ...patch };
        const fns = _listeners.get(key);
        if (fns) fns.forEach(fn => fn(_state[key]));
    },

    reset(key) {
        const empty = Array.isArray(_state[key]) ? [] : {};
        this.set(key, empty);
    },

    subscribe(key, fn) {
        if (!_listeners.has(key)) _listeners.set(key, []);
        _listeners.get(key).push(fn);
        return () => {
            const arr = _listeners.get(key);
            const i   = arr.indexOf(fn);
            if (i !== -1) arr.splice(i, 1);
        };
    },

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