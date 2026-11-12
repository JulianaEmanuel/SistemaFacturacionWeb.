(() => {
    const key = 'sistemaweb.theme';
    const root = document.documentElement;
    const system = window.matchMedia('(prefers-color-scheme: dark)');
    let preference;
    try { preference = localStorage.getItem(key); } catch (_) { /* La preferencia sigue funcionando sin almacenamiento. */ }
    if (!['light', 'dark'].includes(preference)) preference = null;
    const buttons = () => document.querySelectorAll('[data-theme-toggle]');
    const apply = (theme) => {
        root.setAttribute('data-bs-theme', theme);
        buttons().forEach(button => {
            const dark = theme === 'dark';
            const label = dark ? 'Activar modo claro' : 'Activar modo oscuro';
            button.hidden = false;
            button.setAttribute('aria-pressed', String(dark));
            button.setAttribute('aria-label', label);
            button.title = label;
            const icon = button.querySelector('[data-theme-icon]');
            if (icon) icon.className = dark ? 'bi bi-sun' : 'bi bi-moon-stars';
        });
    };
    // Se aplica en el head antes de pintar para evitar destellos claros al navegar.
    apply(preference || (system.matches ? 'dark' : 'light'));
    const bind = () => {
        apply(root.getAttribute('data-bs-theme'));
        buttons().forEach(button => button.addEventListener('click', () => {
            preference = root.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
            apply(preference);
            try { localStorage.setItem(key, preference); } catch (_) { /* Sin persistencia disponible. */ }
        }));
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', bind, {once: true});
    else bind();
    system.addEventListener('change', event => { if (!preference) apply(event.matches ? 'dark' : 'light'); });
    window.addEventListener('storage', event => {
        if (event.key !== key && event.key !== null) return;
        preference = ['light', 'dark'].includes(event.newValue) ? event.newValue : null;
        apply(preference || (system.matches ? 'dark' : 'light'));
    });
})();
