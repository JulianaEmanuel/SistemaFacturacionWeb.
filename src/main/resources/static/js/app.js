const currentDate = document.getElementById("current-date");

// Un solo comportamiento de navegación para todas las pantallas.
const appSidebar = document.getElementById("appSidebar");
if (appSidebar) {
	const closeSidebar = () => {
		if (window.bootstrap?.Offcanvas) window.bootstrap.Offcanvas.getOrCreateInstance(appSidebar).hide();
		else appSidebar.classList.remove("show");
	};
	appSidebar.querySelectorAll('[data-bs-dismiss="offcanvas"]').forEach(button => button.addEventListener("click", closeSidebar));
	appSidebar.querySelectorAll("nav a").forEach(link => link.addEventListener("click", () => {
		if (window.matchMedia("(max-width: 991.98px)").matches) closeSidebar();
	}));
	document.querySelectorAll('[data-bs-target="#appSidebar"][data-bs-toggle="offcanvas"]').forEach(button => {
		appSidebar.addEventListener("shown.bs.offcanvas", () => button.setAttribute("aria-expanded", "true"));
		appSidebar.addEventListener("hidden.bs.offcanvas", () => button.setAttribute("aria-expanded", "false"));
	});
}

if (currentDate) {
	currentDate.textContent = new Intl.DateTimeFormat("es", {
		weekday: "long",
		day: "numeric",
		month: "long",
		year: "numeric"
	}).format(new Date());
}
