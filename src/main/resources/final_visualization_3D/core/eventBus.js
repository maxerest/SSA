// ================================================================
// core/eventBus.js — Decoupled pub/sub for cross-module actions
// ================================================================

const _handlers = new Map();

export const EventBus = {
    emit(event, payload) {
        const fns = _handlers.get(event);
        if (fns) fns.forEach(fn => fn(payload));
    },

    on(event, fn) {
        if (!_handlers.has(event)) _handlers.set(event, []);
        _handlers.get(event).push(fn);
        return () => {
            const arr = _handlers.get(event);
            const i   = arr.indexOf(fn);
            if (i !== -1) arr.splice(i, 1);
        };
    },

    off(event) {
        _handlers.delete(event);
    },
};

export const Events = {
    PLAY_PAUSE:        'playback:playpause',
    TICK:              'playback:tick',
    SPEED_CHANGED:     'playback:speed',
    SLIDER_CHANGED:    'playback:slider',
    SAT_SELECTED:      'selection:sat',
    SAT_DESELECTED:    'selection:deselect',
    SATS_LOADED:       'data:sats',
    GS_LOADED:         'data:gs',
    ORBITAL_LOADED:    'data:orbital',
    SATCOM_LOADED:     'data:satcom',
    EPOCH_SET:         'data:epoch',
    ACTION_PROPAGATE:  'action:propagate',
    ACTION_CONFIGURE:  'action:configure',
    TOGGLE_TRAILS:     'ui:trails',
    TOGGLE_CONES:      'ui:cones',
    STATUS_UPDATE:     'ui:status',
};