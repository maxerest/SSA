/* ================================================================
 * explorer.js  –  CSV file-picker overlay (Java bridge integration)
 *
 * Depends on: state.js
 * The Java side calls window.populateExplorer(jsonStr) to seed the tree.
 * On load, javaBridge.loadSatCSV(absPath) is called back into Java.
 * ================================================================ */

// ── Event listeners ──────────────────────────────────────────────

document.getElementById('explorerSearch').addEventListener('input', e =>
    renderExplorerTree(e.target.value)
);

document.getElementById('explorerLoadBtn').addEventListener('click', () => {
    if (!explorerSelected) return;
    if (window.javaBridge) window.javaBridge.loadSatCSV(explorerSelected.absPath);
    document.getElementById('explorerOverlay').classList.add('hidden');
});

document.getElementById('explorerSkip').addEventListener('click', () => {
    document.getElementById('explorerOverlay').classList.add('hidden');
});
