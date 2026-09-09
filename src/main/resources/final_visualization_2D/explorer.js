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

// Fallback message if Java never calls populateExplorer
setTimeout(() => {
    if (explorerData.length === 0) {
        document.getElementById('explorerTree').innerHTML =
            '<div style="padding:16px;color:#445;font-size:12px;">' +
            'Waiting for Java bridge…<br>Use the Load CSV button instead.</div>';
    }
}, 800);