/* ================================================================
 * explorer.js  –  CSV file-picker overlay (Java bridge integration)
 *
 * Depends on: state.js
 * The Java side calls window.populateExplorer(jsonStr) to seed the tree.
 * On load, javaBridge.loadSatCSV(absPath) is called back into Java.
 * ================================================================ */

/**
 * Called from Java after the page loads.
 * Receives a JSON string describing the folder tree.
 * @param {string} jsonStr - JSON array of {folder, path, files[]} objects
 */
window.populateExplorer = function(jsonStr) {
    explorerData = JSON.parse(jsonStr);
    renderExplorerTree('');
};

/**
 * Render (or re-render) the file tree, optionally filtered by a query string.
 * @param {string} filter - case-insensitive substring to match folder/file names
 */
function renderExplorerTree(filter) {
    const tree = document.getElementById('explorerTree');
    tree.innerHTML = '';
    const q = filter.toLowerCase().trim();

    explorerData.forEach(folder => {
        const files = folder.files.filter(f =>
            !q || f.toLowerCase().includes(q) || folder.folder.toLowerCase().includes(q)
        );
        if (files.length === 0) return;

        // Folder row
        const folderEl = document.createElement('div');
        folderEl.className = 'ex-folder';
        folderEl.innerHTML = `<span class="icon">&#9656;</span><span>${folder.folder}</span>`;
        tree.appendChild(folderEl);

        // File rows
        files.forEach(file => {
            const absPath = folder.path + '/' + file;
            const fileEl  = document.createElement('div');
            const isSelected = explorerSelected && explorerSelected.absPath === absPath;
            fileEl.className = 'ex-file' + (isSelected ? ' selected' : '');
            fileEl.innerHTML = `<span class="icon">&#9632;</span><span>${file}</span>`;
            fileEl.addEventListener('click', () => {
                explorerSelected = { folder: folder.folder, file, absPath };
                document.getElementById('explorerSelectedLabel').textContent =
                    `${folder.folder} / ${file}`;
                document.getElementById('explorerLoadBtn').disabled = false;
                tree.querySelectorAll('.ex-file').forEach(el => el.classList.remove('selected'));
                fileEl.classList.add('selected');
            });
            tree.appendChild(fileEl);
        });
    });

    if (!tree.children.length) {
        tree.innerHTML = '<div style="padding:16px;color:#445;font-size:12px;">No files match</div>';
    }
}

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