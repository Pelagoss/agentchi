// Page d'accueil : montres vivantes dessinées avec les grilles de l'appli (mascot.js, screens.js, watch.js),
// la journée qui défile, la personnalisation de la mascotte, les onglets du guide et les boutons Copier.
(function () {
  'use strict';
  const C = Screens.C, COL = Screens.COL;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const tr = Mascot.tr; // textes selon <html lang>
  const dpr = () => Math.min(window.devicePixelRatio || 1, 2.5);

  // --- Mascotte choisie (Crabe Océan, comme l'icône de l'appli) ---
  const DEFAULT_LOOK = { species: 'CRAB', palette: 'OCEAN', accessory: 'PARTY_HAT' };
  let look = { ...DEFAULT_LOOK };
  try {
    const saved = JSON.parse(localStorage.getItem('agentchi.look') || 'null');
    if (saved && Mascot.SPECIES[saved.species] && Mascot.PALETTES[saved.palette] && Mascot.ACCESSORIES[saved.accessory]) look = saved;
  } catch (e) { /* stockage indisponible : mascotte par défaut */ }
  const saveLook = () => { try { localStorage.setItem('agentchi.look', JSON.stringify(look)); } catch (e) { /* ignoré */ } };

  // --- Animation (mêmes courbes que la vidéo des stores) ---
  const bounceAt = (t, period) => { if (reduced) return 0.5; const p = (t % (2 * period)) / period; return p < 1 ? p : 2 - p; };
  const blinkAt = (t) => !reduced && t % 3.7 > 3.55;
  const pressAt = (t, t0) => { const d = t - t0; return d < 0 || d > 0.35 ? 0 : Math.sin(Math.PI * d / 0.35); };
  const jumpAt = (t, t0) => {
    const d = t - t0; if (reduced || d < 0 || d > 1.4) return 0;
    if (d < 0.14) return d / 0.14;
    const k = d - 0.14; return Math.max(0, Math.exp(-5 * k) * Math.cos(12 * k));
  };
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
  const lerp = (a, b, k) => a + (b - a) * k;
  const easeOut = (k) => 1 - Math.pow(1 - clamp(k), 3);
  const now = () => performance.now() / 1000;
  const periodFor = (r) => ['CELEBRATE', 'PLAY', 'WAVE', 'ALERT'].includes(r) ? 0.38 : r === 'SLEEP' ? 2.4 : 1.2;

  // --- Canvas à la bonne densité ---
  function fit(canvas) {
    const r = canvas.getBoundingClientRect(), k = dpr();
    const w = Math.max(1, Math.round(r.width * k)), h = Math.max(1, Math.round(r.height * k));
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
    return { w, h, k, ctx: canvas.getContext('2d') };
  }

  /** Une montre : écran rendu hors champ (1 ou 2 couches pour le fondu), puis boîtier par Deco.watch. */
  function makeStage(canvas) {
    const layers = [document.createElement('canvas'), document.createElement('canvas')];
    const st = { canvas, layers, geom: null, visible: true };
    st.draw = (paints) => {
      const { w, h, ctx } = fit(canvas);
      const R = Math.min(w / 2.62, h / 2.8);
      const cx = w / 2, cy = h / 2;
      st.geom = { cx, cy, R, k: dpr() };
      const size = Math.ceil(2 * R);
      paints.forEach((p, i) => {
        const off = layers[i];
        if (off.width !== size) { off.width = size; off.height = size; }
        const o = off.getContext('2d');
        o.setTransform(size / Screens.D, 0, 0, size / Screens.D, 0, 0);
        o.globalAlpha = 1;
        p.paint(o);
      });
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, w, h);
      Deco.watch(ctx, cx, cy, R, (c) => {
        paints.forEach((p, i) => { c.globalAlpha = p.alpha; c.drawImage(layers[i], 0, 0, Screens.D, Screens.D); });
        c.globalAlpha = 1;
      });
    };
    /** Coordonnées d'un événement en dp d'écran (0..225), ou null hors de l'écran. */
    st.toDp = (ev) => {
      if (!st.geom) return null;
      const r = canvas.getBoundingClientRect(), { cx, cy, R, k } = st.geom;
      const x = ((ev.clientX - r.left) * k - (cx - R)) / (2 * R) * Screens.D;
      const y = ((ev.clientY - r.top) * k - (cy - R)) / (2 * R) * Screens.D;
      return Math.hypot(x - C, y - C) <= C ? { x, y } : null;
    };
    return st;
  }

  // --- Héros : Chi à soigner pour de vrai ---
  const pet = {
    reaction: 'NONE', until: 0, moodLine: undefined, sleeping: false, jumpAt: -9,
    press: { feed: -9, play: -9, sleep: -9, clean: -9 },
    target: { satiety: 72, energy: 64, happiness: 86 }, shown: { satiety: 72, energy: 64, happiness: 86 },
    // Comme dans l'appli, il fait caca de temps en temps (un premier peu après l'arrivée, puis après chaque repas) ;
    // le bouton Nettoyer apparaît alors sous les actions, en faisant défiler l'écran.
    poops: 0, poopAt: now() + 9, scroll: 0,
  };
  const SCROLL = 36; // dp de défilement pour montrer « Nettoyer »
  const careButtons = document.querySelectorAll('[data-care]');
  function syncCareButtons() {
    careButtons.forEach((b) => {
      const kind = b.dataset.care;
      if (kind === 'sleep') {
        b.setAttribute('aria-pressed', String(pet.sleeping));
        b.querySelector('span').textContent = pet.sleeping ? tr('Réveil', 'Wake up') : tr('Dodo', 'Sleep');
      } else if (kind === 'clean') {
        b.hidden = pet.poops === 0;
        b.querySelector('span').textContent = pet.poops > 1 ? `Nettoyer (${pet.poops})` : 'Nettoyer';
      } else {
        b.disabled = pet.sleeping;
      }
    });
  }
  function care(kind) {
    const t = now(), g = pet.target;
    const react = (r, dur, line) => { pet.reaction = r; pet.until = dur ? t + dur : Infinity; pet.moodLine = line; };
    if (kind === 'clean') {
      if (!pet.poops) return;
      pet.press.clean = t; g.happiness = Math.min(100, g.happiness + 5 * pet.poops); pet.poops = 0;
      if (!pet.sleeping) react('CLEAN', 1.8);
    } else if (kind === 'sleep') {
      pet.press.sleep = t;
      pet.sleeping = !pet.sleeping;
      if (pet.sleeping) react('SLEEP', 0); else { g.energy = 100; react('WAVE', 2.2, tr('Coucou !', 'Hi there!')); }
    } else if (pet.sleeping) {
      return;
    } else if (kind === 'feed') {
      pet.press.feed = t; g.satiety = Math.min(100, g.satiety + 18); react('EAT', 2.4);
      if (pet.poopAt === Infinity) pet.poopAt = t + 5;
    } else if (kind === 'play') {
      pet.press.play = t; g.happiness = Math.min(100, g.happiness + 10); g.energy = Math.max(8, g.energy - 8); react('PLAY', 2.4);
    } else if (kind === 'pet') {
      pet.jumpAt = t; g.happiness = Math.min(100, g.happiness + 5); react('LOVE', 1.8);
    }
    syncCareButtons();
  }
  careButtons.forEach((b) => b.addEventListener('click', () => care(b.dataset.care)));

  function heroPaint(t) {
    if (pet.until !== Infinity && t > pet.until) { pet.reaction = pet.sleeping ? 'SLEEP' : 'NONE'; pet.moodLine = undefined; pet.until = Infinity; }
    if (t > pet.poopAt && pet.poops < 3) {
      pet.poops++; pet.poopAt = Infinity; pet.target.happiness = Math.max(10, pet.target.happiness - 6);
      syncCareButtons();
    }
    for (const key in pet.shown) pet.shown[key] = lerp(pet.shown[key], pet.target[key], 0.08);
    pet.scroll = reduced ? (pet.poops ? SCROLL : 0) : lerp(pet.scroll, pet.poops ? SCROLL : 0, 0.08);
    // À partir de deux cacas, il boude (« Ça sent pas bon ici… »), sauf réaction en cours.
    const reaction = pet.reaction === 'NONE' && pet.poops >= 2 ? 'DIRTY' : pet.reaction;
    return (c) => Screens.pet(c, {
      look, reaction, moodLine: pet.moodLine, time: clock(), poops: pet.poops, scroll: pet.scroll,
      bounce: bounceAt(t, periodFor(reaction)), blink: blinkAt(t), jump: jumpAt(t, pet.jumpAt),
      satiety: pet.shown.satiety, energy: pet.shown.energy, happiness: pet.shown.happiness,
      pressed: { feed: pressAt(t, pet.press.feed), play: pressAt(t, pet.press.play), sleep: pressAt(t, pet.press.sleep), clean: pressAt(t, pet.press.clean) },
    });
  }
  function clock() {
    const d = new Date();
    return d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
  }
  function heroHit(p) {
    if (!p) return null;
    const y = p.y + pet.scroll; // coordonnées dans la liste qui défile
    if (pet.poops && Math.abs(p.x - C) < 62 && y > 222 && y < 252) return 'clean';
    const buttons = [['feed', C - 44], ['play', C], ['sleep', C + 44]];
    for (const [kind, x] of buttons) if (Math.hypot(p.x - x, y - 194) < 21) return kind;
    if (p.x > C - 58 && p.x < C + 58 && y > 18 && y < 104) return 'pet';
    return null;
  }

  // --- La journée : un écran par étape ---
  const DAY = {
    permission: (t) => (c) => Screens.permission(c, { time: '10:09', pressed: pressAt(t % 4, 2.4) }),
    question: (t) => (c) => questionScreen(c, t),
    sessions: () => (c) => Screens.sessions(c, { time: '10:20' }),
    celebrate: (t) => (c) => {
      Screens.pet(c, { look, reaction: 'CELEBRATE', time: '10:31', level: 4, bounce: bounceAt(t, 0.38), satiety: 70, energy: 58, happiness: 94 });
      const k = reduced ? 0.3 : (t % 2.6) / 2.6;
      Screens.text(c, '+10 XP', C + 62, 52 - easeOut(k) * 16, { size: 12, weight: 700, color: '#F2C14E', alpha: 1 - k });
    },
    // 12 s en boucle : il a faim, on le nourrit, deux cacas, il boude, l'écran défile jusqu'à Nettoyer, tout propre.
    hungry: (t) => (c) => {
      const p = reduced ? 7 : t % 12;
      const poops = p < 4.6 || p >= 7.9 ? 0 : p < 5.6 ? 1 : 2;
      const reaction = p < 1.8 ? 'HUNGRY' : p < 4 ? 'EAT' : poops >= 2 ? 'DIRTY' : p >= 7.9 && p < 9.8 ? 'CLEAN' : 'NONE';
      const scroll = SCROLL * (p < 6 ? 0 : p < 8.1 ? easeOut((p - 6) / 0.7) : 1 - easeOut((p - 8.1) / 0.7));
      Screens.pet(c, {
        look, time: '12:40', reaction, moodLine: reaction === 'HUNGRY' ? tr("J'ai faim…", 'I’m hungry…') : undefined, poops, scroll,
        satiety: p < 1.8 ? 18 : lerp(18, 62, easeOut((p - 1.8) / 1.2)), energy: 60,
        happiness: poops ? lerp(70, 52, easeOut((p - 4.6) / 2)) : p >= 7.9 ? lerp(52, 72, easeOut((p - 7.9) / 1.2)) : 70,
        bounce: bounceAt(t, periodFor(reaction)), blink: blinkAt(t), pressed: { feed: pressAt(p, 1.8), clean: pressAt(p, 7.6) },
      });
    },
    achievements: () => (c) => Screens.achievements(c, { time: '18:55' }),
    sleep: (t) => (c) => Screens.pet(c, {
      look, time: '23:12', reaction: 'SLEEP', bounce: bounceAt(t, 2.4), energy: lerp(38, 92, easeOut(reduced ? 1 : (t % 12) / 12)),
      satiety: 64, happiness: 88, dim: 0.6,
    }),
  };

  function questionScreen(c, t) {
    const p = reduced ? 3 : t % 6, picked = p > 2;
    c.fillStyle = '#000'; c.fillRect(0, 0, Screens.D, Screens.D);
    Screens.text(c, '10:14', C, 17, { size: 11, weight: 600 });
    Screens.text(c, '❓ Question', C, 42, { size: 11.5, weight: 600, color: COL.orange });
    Screens.text(c, 'Claude · agentchi · ⏱ 1:58', C, 55, { size: 10, color: COL.variant });
    Screens.text(c, tr('Quelle base pour les tests ?', 'Which database for the tests?'), C, 74, { size: 12.5 });
    Screens.chip(c, 84, 42, { icon: picked ? '☑️' : '⬜', label: 'SQLite', secondary: tr('En mémoire, rapide', 'In memory, fast'), primary: picked, width: 184, pressed: pressAt(p, 2) });
    Screens.chip(c, 131, 42, { icon: '⬜', label: 'PostgreSQL', secondary: tr('Comme en production', 'Same as production'), width: 184 });
    Screens.chip(c, 178, 38, { icon: '✍️', label: tr('Autre réponse', 'Other answer'), width: 184 });
  }

  const day = { key: 'permission', since: 0, prev: null, prevSince: 0, fadeAt: -9 };
  function setDay(key) {
    if (key === day.key || !DAY[key]) return;
    const t = now();
    day.prev = day.key; day.prevSince = day.since;
    day.key = key; day.since = t; day.fadeAt = t;
  }

  // --- Personnalisation ---
  const studio = { changedAt: -9, highlight: null };
  function studioPaint(t) {
    return (c) => Screens.mascotSettings(c, {
      look, level: 5, time: clock(), highlight: studio.highlight, pressed: pressAt(t, studio.changedAt),
      bounce: bounceAt(t, 1.2), jump: jumpAt(t, studio.changedAt),
    });
  }

  function drawMini(canvas, lk, t, opts = {}) {
    const { w, h, ctx } = fit(canvas);
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, w, h);
    const px = Math.max(1, Math.floor(Math.min(w / Mascot.WIDTH, h / (Mascot.HEIGHT + 0.6))));
    const b = opts.still ? 0 : Math.round(bounceAt(t, 0.6) * px * 0.5);
    const x = Math.round((w - Mascot.WIDTH * px) / 2), y = Math.round((h - Mascot.HEIGHT * px) / 2) - b;
    Mascot.draw(ctx, lk, opts.face || (blinkAt(t) ? 'CLOSED' : 'HAPPY'), x, y, px, { altLegs: !opts.still && bounceAt(t, 0.6) > 0.5 });
  }

  function buildPicker() {
    const form = document.querySelector('[data-picker]');
    if (!form) return;
    const groups = {
      species: Object.entries(Mascot.SPECIES).map(([id, s]) => ({ id, label: s.label })),
      palette: Object.entries(Mascot.PALETTES).map(([id, p]) => ({ id, label: p.label, body: p.body, accent: p.accent })),
      accessory: Object.entries(Mascot.ACCESSORIES).map(([id, a]) => ({ id, label: a.label, level: a.level })),
    };
    const previews = [];
    for (const [group, items] of Object.entries(groups)) {
      const box = form.querySelector(`[data-opts="${group}"]`);
      items.forEach((it) => {
        const wrap = document.createElement('div'); wrap.className = 'opt';
        const input = document.createElement('input');
        input.type = 'radio'; input.name = group; input.value = it.id; input.id = `look-${group}-${it.id}`;
        input.checked = look[group] === it.id;
        const label = document.createElement('label'); label.htmlFor = input.id;
        if (group === 'palette') {
          const sw = document.createElement('span'); sw.className = 'swatch';
          sw.style.setProperty('--sw-body', it.body); sw.style.setProperty('--sw-accent', it.accent);
          label.append(sw);
        } else {
          const cv = document.createElement('canvas'); cv.setAttribute('aria-hidden', 'true');
          label.append(cv);
          previews.push({ cv, group, id: it.id });
        }
        const text = document.createElement('span'); text.textContent = it.label;
        label.append(text);
        if (it.level > 1) { const sm = document.createElement('small'); sm.textContent = `${tr('niv.', 'Lv.')} ${it.level}`; label.append(' ', sm); }
        input.addEventListener('change', () => {
          look = { ...look, [group]: it.id };
          studio.changedAt = now(); studio.highlight = group;
          saveLook(); drawPreviews(); describe();
        });
        wrap.append(input, label); box.append(wrap);
      });
    }
    function drawPreviews() {
      previews.forEach(({ cv, group, id }) => drawMini(cv, { ...look, [group]: id }, 0, { still: true, face: 'HAPPY' }));
    }
    function describe() {
      const note = document.querySelector('[data-look-label]');
      if (!note) return;
      const s = Mascot.SPECIES[look.species].label, p = Mascot.PALETTES[look.palette].label, a = Mascot.ACCESSORIES[look.accessory];
      note.innerHTML = '';
      const b = document.createElement('b'); b.textContent = tr(`${s} ${p}`, `${p} ${s}`);
      note.append(b, look.accessory === 'NONE' ? tr(', sans accessoire', ', no accessory') : `, ${a.label.toLowerCase()}`,
        tr(' · une combinaison sur 108.', ' · one of 108 combinations.'));
    }
    drawPreviews(); describe();
    window.addEventListener('resize', drawPreviews);
  }

  // --- Onglets du guide (sans JS, tous les panneaux restent visibles) ---
  function buildTabs() {
    document.querySelectorAll('[data-tabs]').forEach((root) => {
      const tabs = [...root.querySelectorAll('[role="tab"]')];
      const select = (tab, focus) => {
        tabs.forEach((t) => {
          const on = t === tab;
          t.setAttribute('aria-selected', String(on)); t.tabIndex = on ? 0 : -1;
          document.getElementById(t.getAttribute('aria-controls')).hidden = !on;
        });
        if (focus) tab.focus();
      };
      tabs.forEach((tab, i) => {
        tab.addEventListener('click', () => select(tab));
        tab.addEventListener('keydown', (e) => {
          const step = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
          if (step) { e.preventDefault(); select(tabs[(i + step + tabs.length) % tabs.length], true); }
          if (e.key === 'Home') { e.preventDefault(); select(tabs[0], true); }
          if (e.key === 'End') { e.preventDefault(); select(tabs[tabs.length - 1], true); }
        });
      });
      select(tabs.find((t) => t.getAttribute('aria-selected') === 'true') || tabs[0]);
    });
  }

  // --- Copier ---
  function buildCopy() {
    document.querySelectorAll('.copy').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const target = btn.dataset.copy ? document.querySelector(btn.dataset.copy) : btn.parentElement.querySelector('pre');
        const text = target.innerText.trim();
        try {
          await navigator.clipboard.writeText(text);
          btn.textContent = tr('Copié', 'Copied'); btn.classList.add('done');
        } catch (e) {
          const range = document.createRange(); range.selectNodeContents(target);
          const sel = getSelection(); sel.removeAllRanges(); sel.addRange(range);
          btn.textContent = tr('Sélectionné', 'Selected');
        }
        setTimeout(() => { btn.textContent = tr('Copier', 'Copy'); btn.classList.remove('done'); }, 1800);
      });
    });
  }

  // --- Vidéo : lecture muette en boucle dès qu'elle est à l'écran, puis « Activer le son » la relance avec le son ---
  function buildVideo() {
    const btn = document.querySelector('[data-play]');
    const video = btn && btn.parentElement.querySelector('video');
    if (!video) return;
    const label = btn.querySelector('[data-play-label]'), dur = btn.querySelector('[data-play-dur]');
    const saveData = navigator.connection && navigator.connection.saveData;
    let mode = 'idle'; // idle → auto (muet, en boucle) → user (son, contrôles natifs)
    video.controls = false;
    btn.hidden = false;

    const withSound = () => {
      mode = 'user';
      video.muted = false; video.loop = false; video.controls = true;
      if (!video.paused) video.currentTime = 0;
      btn.hidden = true;
      video.play().catch(() => { /* lecture refusée : les contrôles restent disponibles */ });
    };
    btn.addEventListener('click', withSound);

    if (reduced || saveData || !('IntersectionObserver' in window)) return;
    new IntersectionObserver((entries) => entries.forEach((e) => {
      if (e.isIntersecting) {
        if (mode === 'idle') {
          mode = 'auto';
          video.muted = true; video.loop = true; video.preload = 'auto';
          label.textContent = tr('Activer le son', 'Turn on sound'); dur.hidden = true;
          btn.querySelector('svg').innerHTML = '<path d="M2 6h3l4-3.5v11L5 10H2z"/><path class="wave" d="M11 5.5a3.5 3.5 0 0 1 0 5M12.8 3.5a6 6 0 0 1 0 9"/>';
          btn.classList.add('sound');
        }
        if (mode === 'auto') video.play().catch(() => { mode = 'idle'; label.textContent = tr('Lancer la vidéo', 'Play the video'); dur.hidden = false; });
      } else if (!video.paused) {
        video.pause();
      }
    }), { threshold: 0.6 }).observe(video);
  }

  // --- Démarrage ---
  async function start() {
    try {
      await Promise.all(['500 12px Inter', '600 12px Inter', '700 12px Inter', '500 11px "JetBrains Mono"'].map((f) => document.fonts.load(f)));
    } catch (e) { /* polices système en secours */ }

    const stages = {};
    document.querySelectorAll('[data-watch]').forEach((cv) => { stages[cv.dataset.watch] = makeStage(cv); });
    const minis = [...document.querySelectorAll('[data-mini-pet]')];
    document.querySelectorAll('[data-icon]').forEach((cv) => {
      const { w, ctx } = fit(cv);
      Mascot.drawIcon(ctx, cv.dataset.icon, w / 2, w / 2, w);
    });

    // Ne dessiner que ce qui est à l'écran.
    const io = new IntersectionObserver((entries) => entries.forEach((e) => {
      const st = Object.values(stages).find((s) => s.canvas === e.target);
      if (st) st.visible = e.isIntersecting;
    }));
    Object.values(stages).forEach((s) => io.observe(s.canvas));

    const hero = stages.hero;
    if (hero) {
      hero.canvas.addEventListener('click', (ev) => { const k = heroHit(hero.toDp(ev)); if (k) care(k); });
      hero.canvas.addEventListener('pointermove', (ev) => hero.canvas.classList.toggle('hot', !!heroHit(hero.toDp(ev))));
    }

    const steps = [...document.querySelectorAll('.step')];
    const stepIo = new IntersectionObserver((entries) => entries.forEach((e) => {
      if (!e.isIntersecting) return;
      steps.forEach((s) => s.classList.toggle('on', s === e.target));
      setDay(e.target.dataset.screen);
    }), { rootMargin: '-45% 0px -45% 0px' });
    steps.forEach((s) => stepIo.observe(s));
    if (steps[0]) steps[0].classList.add('on');

    const top = document.querySelector('.top');
    const onScroll = () => top.classList.toggle('scrolled', scrollY > 8);
    addEventListener('scroll', onScroll, { passive: true }); onScroll();

    buildPicker();
    let last = 0;
    function frame() {
      const t = now();
      if (hero && hero.visible) hero.draw([{ paint: heroPaint(t), alpha: 1 }]);
      const d = stages.day;
      if (d && d.visible) {
        const k = reduced ? 1 : easeOut((t - day.fadeAt) / 0.45);
        const current = { paint: DAY[day.key](t - day.since), alpha: k };
        d.draw(k < 1 && day.prev ? [{ paint: DAY[day.prev](t - day.prevSince), alpha: 1 }, current] : [{ ...current, alpha: 1 }]);
      }
      if (stages.studio && stages.studio.visible) stages.studio.draw([{ paint: studioPaint(t), alpha: 1 }]);
      if (t - last > 1 / 12) { minis.forEach((m) => drawMini(m, look, t)); last = t; }
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  buildTabs();
  buildCopy();
  buildVideo();
  syncCareButtons();
  start();
})();
