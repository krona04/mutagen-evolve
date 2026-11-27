const search = document.querySelector(".wiki-search");
const query = document.querySelector("#query");
const status = document.querySelector("#search-status");
const results = document.querySelector("#search-results");
const limit = 8;

function normalize(value) {
  return value.toLocaleLowerCase(document.documentElement.lang)
    .normalize("NFD")
    .replace(/[\u0300\u0301\u0302\u0303\u0306\u0308\u0309\u0323]/gu, "")
    .normalize("NFC");
}

const sections = window.mutagenWiki.map((entry) => ({
  ...entry,
  heading: normalize(entry.title + " " + entry.page),
  content: normalize(entry.text),
}));

function makeResult(entry, terms) {
  const item = document.createElement("li");
  const link = document.createElement("a");
  const category = document.createElement("span");
  const snippet = document.createElement("span");
  const hit = terms.map((term) => entry.content.indexOf(term)).find((place) => place >= 0);
  const start = hit === undefined ? 0 : Math.max(0, hit - 40);
  const end = Math.min(start + 142, entry.text.length);

  link.href = entry.url;
  link.textContent = entry.title;
  category.className = "result-category";
  category.textContent = entry.page;
  snippet.textContent = (start ? "…" : "") + entry.text.slice(start, end) + (end < entry.text.length ? "…" : "");
  link.append(category, snippet);
  item.append(link);
  return item;
}

function updateSearch() {
  const terms = [...new Set(normalize(query.value).match(/[\p{L}\p{N}_-]+/gu) || [])];
  results.replaceChildren();
  status.textContent = "";
  results.hidden = true;
  if (terms.length === 0) return;

  const matches = sections
    .filter((entry) => terms.every((term) => entry.heading.includes(term) || entry.content.includes(term)))
    .map((entry) => ({
      entry,
      score: terms.filter((term) => entry.heading.includes(term)).length,
    }))
    .sort((a, b) => b.score - a.score);

  if (matches.length === 0) {
    status.textContent = search.dataset.noMatch;
    return;
  }

  const shown = matches.slice(0, limit);
  const number = new Intl.NumberFormat(document.documentElement.lang);
  status.textContent = `${search.dataset.countLabel}: ${number.format(shown.length)} / ${number.format(matches.length)}`;
  results.append(...shown.map(({ entry }) => makeResult(entry, terms)));
  results.hidden = false;
}

query.addEventListener("input", updateSearch);
query.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    query.value = "";
    updateSearch();
  }
});
window.addEventListener("hashchange", () => {
  query.value = "";
  updateSearch();
});
search.hidden = false;
