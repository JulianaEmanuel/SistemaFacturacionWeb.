(() => {
    "use strict";
    const input = document.getElementById("password");
    const toggle = document.getElementById("password-toggle");
    if (!input || !toggle) return;
    toggle.addEventListener("click", () => {
        const visible = input.type === "password";
        input.type = visible ? "text" : "password";
        const label = visible ? "Ocultar contraseña" : "Mostrar contraseña";
        toggle.setAttribute("aria-pressed", String(visible));
        toggle.setAttribute("aria-label", label);
        toggle.title = label;
    });
})();
