// ================================================================
// core/eventBus.js — Decoupled pub/sub for cross-module actions
// ================================================================
//
// UI components emit actions; transport layers (WebSocket) subscribe.
// Nothing in /ui/ should import from /comms/ or vice-versa.
//
// Canonical event names (use these constants, not raw strings):
//   EventBus.emit(Events.ACTION_PROPAGATE)
//   EventBus.emit(Events.ACTION_CONFIGURE)
//   EventBus.emit(Events.SAT_SELECTED, satName)
//   EventBus.emit(Events.SAT_DESELECTED)
//   EventBus.emit(Events.TICK)

const _handlers = new Map();

export const EventBus = {
    /**
     * @param {string} event
     * @param {*}      payload
     */
    emit(event, payload) {
        const fns = _handlers.get(event);
        if (fns) fns.forEach(fn => fn(payload));
    },

    /**
     * @param {string}   event
     * @param {Function} fn
     * @returns {Function} unsubscribe
     */
    on(event, fn) {
        if (!_handlers.has(event)) _handlers.set(event, []);
        _handlers.get(event).push(fn);
        return () => {
            const arr = _handlers.get(event);
            const i   = arr.indexOf(fn);
            if (i !== -1) arr.splice(i, 1);
        };
    },

    /** Remove every listener for an event (useful in tests) */
    off(event) {
        _handlers.delete(event);
    },
};

/** All event name constants — single source of truth */
export const Events = {
    // Playback
    PLAY_PAUSE:        'playback:playpause',
    TICK:              'playback:tick',
    SPEED_CHANGED:     'playback:speed',
    SLIDER_CHANGED:    'playback:slider',

    // Selection
    SAT_SELECTED:      'selection:sat',
    SAT_DESELECTED:    'selection:deselect',

    // Data loaded
    SATS_LOADED:       'data:sats',
    GS_LOADED:         'data:gs',
    ORBITAL_LOADED:    'data:orbital',
    SATCOM_LOADED:     'data:satcom',
    EPOCH_SET:         'data:epoch',

    // Actions sent to backend
    ACTION_PROPAGATE:  'action:propagate',
    ACTION_CONFIGURE:  'action:configure',

    // UI toggles
    TOGGLE_TRAILS:     'ui:trails',
    TOGGLE_CONES:      'ui:cones',

    // Status
    STATUS_UPDATE:     'ui:status',
};