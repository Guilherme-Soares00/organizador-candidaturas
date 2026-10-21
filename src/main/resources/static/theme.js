(() => {
  const root = document.documentElement;
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  let preference = null;
  try {
    const stored = localStorage.getItem('proxima-theme');
    if (stored === 'light' || stored === 'dark') preference = stored;
  } catch {}

  function apply(theme) {
    root.dataset.theme = theme;
    const dark = theme === 'dark';
    const button = document.getElementById('theme-toggle');
    if (button) {
      const label = dark ? 'Ativar modo claro' : 'Ativar modo escuro';
      button.setAttribute('aria-label', label);
      button.setAttribute('aria-pressed', String(dark));
      button.title = label;
      button.querySelector('span').textContent = dark ? '☀' : '☾';
    }
    document.querySelector('meta[name="theme-color"]').content = dark ? '#111917' : '#f5f7f3';
  }

  apply(preference ?? (system.matches ? 'dark' : 'light'));
  system.addEventListener('change', event => {
    if (!preference) apply(event.matches ? 'dark' : 'light');
  });
  document.addEventListener('DOMContentLoaded', () => {
    apply(root.dataset.theme);
    document.getElementById('theme-toggle').addEventListener('click', () => {
      preference = root.dataset.theme === 'dark' ? 'light' : 'dark';
      try {
        localStorage.setItem('proxima-theme', preference);
      } catch {}
      apply(preference);
    });
  });
})();
