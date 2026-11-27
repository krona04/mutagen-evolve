(() => {
  const { siteRoot, page, autoLocale } = document.currentScript.dataset;
  const root = new URL(siteRoot, document.baseURI);
  const choices = [...document.querySelectorAll(".language-menu a[data-locale]")];
  const supported = new Set(choices.map((link) => link.dataset.locale));
  const storageKey = "mutagen-evolve.language";

  for (const link of choices) {
    link.addEventListener("click", () => {
      updateLinks();
      try {
        localStorage.setItem(storageKey, link.dataset.locale);
      } catch {
        // The link still opens the chosen language when browser storage is blocked.
      }
    });
  }

  function updateLinks() {
    for (const link of choices) {
      const target = new URL(link.href);
      target.search = location.search;
      target.hash = location.hash;
      link.href = target.href;
    }
  }
  updateLinks();
  window.addEventListener("hashchange", updateLinks);

  if (autoLocale !== "true") return;

  let preferred;
  try {
    preferred = localStorage.getItem(storageKey);
  } catch {
    // The English page remains available when browser storage is blocked.
  }

  if (!supported.has(preferred) || preferred === document.documentElement.lang.toLowerCase()) return;

  const destination = new URL(`lang/${preferred}/${page}`, root);
  destination.search = location.search;
  destination.hash = location.hash;
  location.replace(destination.href);
})();
