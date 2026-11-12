(() => {
    const input = document.getElementById('profile-photo');
    const preview = document.getElementById('profile-preview');
    const error = document.getElementById('profile-photo-error');
    if (!input || !preview || !error) return;
    const original = preview.src;
    let url;
    input.addEventListener('change', () => {
        if (url) { URL.revokeObjectURL(url); url = undefined; }
        preview.src = original;
        error.hidden = true;
        error.textContent = '';
        input.setCustomValidity('');
        const file = input.files[0];
        if (!file) return;
        const message = !['image/jpeg', 'image/png'].includes(file.type)
            ? 'Selecciona una imagen JPG o PNG.'
            : file.size > 2 * 1024 * 1024 ? 'La foto debe pesar como máximo 2 MB.' : '';
        if (message) {
            error.textContent = message;
            error.hidden = false;
            input.setCustomValidity(message);
            return;
        }
        url = URL.createObjectURL(file);
        preview.src = url;
    });
    window.addEventListener('pagehide', () => { if (url) URL.revokeObjectURL(url); });
})();
