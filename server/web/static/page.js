// Guides : en-tête qui se détache au défilement et boutons Copier (comme sur la page d'accueil).
(function () {
  'use strict';
  const fr = document.documentElement.lang === 'fr';
  const tr = (f, e) => (fr ? f : e);

  const top = document.querySelector('.top');
  const onScroll = () => top.classList.toggle('scrolled', scrollY > 8);
  addEventListener('scroll', onScroll, { passive: true }); onScroll();

  document.querySelectorAll('.copy').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const target = btn.parentElement.querySelector('pre');
      try {
        await navigator.clipboard.writeText(target.innerText.trim());
        btn.textContent = tr('Copié', 'Copied'); btn.classList.add('done');
      } catch (e) {
        const range = document.createRange(); range.selectNodeContents(target);
        const sel = getSelection(); sel.removeAllRanges(); sel.addRange(range);
        btn.textContent = tr('Sélectionné', 'Selected');
      }
      setTimeout(() => { btn.textContent = tr('Copier', 'Copy'); btn.classList.remove('done'); }, 1800);
    });
  });
})();
