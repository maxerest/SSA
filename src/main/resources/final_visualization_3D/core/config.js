// ================================================================
// core/config.js — App-wide constants
// ================================================================

export const EARTH_R      = 6_378_100;       // metres
export const TRAIL_LENGTH = 40;

export const SAT_COLORS = [
    '#4fc3f7','#ffb74d','#f48fb1','#ce93d8','#80cbc4','#fff176','#ff8a65',
    '#e57373','#64b5f6','#90a17f','#ffd54f','#4db6ac','#ba68c8','#ff8a65','#90a4ae',
    '#f06292','#4dd0e1','#dce775','#ffcc02','#26c6da','#ab47bc','#ef5350','#42a5f5',
    '#ccb465','#ffa726','#26a69a','#7e57c2','#ec407a','#29b6f6','#d4e157','#ff7043',
    '#00bcd4','#8d6e63','#78909c','#66bbbb','#ffc107','#5c6bc0','#26c6da','#d81b60',
    '#00897b','#558b2f','#f57f17','#6a1b9a','#0277bd','#2e7d32','#e65100','#37474f',
    '#ad1457','#00695c'
];

/** Milliseconds per animation frame (≈60 fps) */
export const FRAME_MS = 16;

/** Maximum satcom / EO events to render before "+N more" truncation */
export const MAX_EVENTS_SHOWN = 20;