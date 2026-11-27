(() => {
  const form = document.querySelector(".version-filters");
  const result = document.querySelector(".versions-count");
  const empty = document.querySelector(".empty-releases");
  const releases = [...document.querySelectorAll("[data-release]")];
  const selects = [...form.querySelectorAll("select")];
  const locale = document.documentElement.lang;
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });

  for (const time of document.querySelectorAll(".release-meta time")) {
    time.textContent = date.format(new Date(`${time.dateTime}T00:00:00Z`));
  }

  function readFilters() {
    const params = new URLSearchParams(location.search);
    for (const select of selects) {
      const value = params.get(select.name);
      select.value = [...select.options].some((option) => option.value === value) ? value : "";
    }
  }

  function showResults() {
    const { minecraft, loader, release: version } = Object.fromEntries(new FormData(form));
    let count = 0;
    for (const release of releases) {
      let matches = 0;
      for (const build of release.querySelectorAll("[data-build]")) {
        const visible = (!minecraft || build.dataset.minecraft === minecraft)
          && (!loader || build.dataset.loader === loader);
        build.hidden = !visible;
        if (visible) matches++;
      }
      release.hidden = !matches || Boolean(version && release.dataset.release !== version);
      if (release.hidden) continue;
      count++;
      if (version || location.hash === `#${release.id}`) {
        release.querySelector(".release-changes").open = true;
      }
    }
    empty.hidden = count !== 0;
    result.querySelector("output").textContent = number.format(count);
  }

  function updateFilters() {
    const url = new URL(location.href);
    for (const { name, value } of selects) {
      if (value) url.searchParams.set(name, value);
      else url.searchParams.delete(name);
    }
    url.hash = "";
    if (location.protocol !== "file:") history.replaceState(null, "", url.href);
    showResults();
  }

  form.addEventListener("submit", (event) => event.preventDefault());
  form.addEventListener("change", updateFilters);
  form.addEventListener("reset", (event) => {
    event.preventDefault();
    for (const select of selects) select.value = "";
    updateFilters();
  });
  readFilters();
  showResults();
  form.hidden = false;
  result.hidden = false;
})();
