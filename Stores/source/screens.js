// Écrans Wear OS d'Agentchi redessinés en canvas (unités : dp, écran rond de 225 dp, comme une Galaxy Watch 4).
// Reprend la mise en page des écrans Compose (PetScreen, MascotPage, AskActivity, SessionsScreen, AchievementsScreen).
(function (global) {
  const D = 225, C = D / 2;
  const COL = {
    orange: '#D97757', orangeDark: '#B4593B', cream: '#F0EEE6', ink: '#1F1E1D', surface: '#2B2826',
    variant: '#B8B2A7', low: '#FF6B5E', satiety: '#E8B04A', energy: '#7FB3FF', happiness: '#E97CA0',
  };
  const FONT = '"Inter", "Noto Color Emoji", sans-serif';

  function font(ctx, size, weight = 500) { ctx.font = `${weight} ${size}px ${FONT}`; }

  function text(ctx, s, x, y, { size = 12, weight = 500, color = COL.cream, align = 'center', alpha = 1, maxWidth } = {}) {
    font(ctx, size, weight);
    ctx.fillStyle = color; ctx.globalAlpha = alpha;
    ctx.textAlign = align; ctx.textBaseline = 'alphabetic';
    if (maxWidth) s = ellipsize(ctx, s, maxWidth);
    ctx.fillText(s, x, y);
    ctx.globalAlpha = 1;
  }

  function ellipsize(ctx, s, w) {
    if (ctx.measureText(s).width <= w) return s;
    while (s.length > 1 && ctx.measureText(s + '…').width > w) s = s.slice(0, -1);
    return s + '…';
  }

  function rrect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, r);
  }

  /** Fond noir, cercle de l'écran et heure en haut (TimeText). */
  function frame(ctx, time = '10:09') {
    ctx.fillStyle = '#000'; ctx.fillRect(0, 0, D, D);
    text(ctx, time, C, 17, { size: 11, weight: 600 });
  }

  /** Échelle et opacité d'un élément de ScalingLazyColumn selon sa position verticale (bords réduits). */
  function edge(cy) {
    const d = Math.abs(cy - C) / C;
    const k = d < 0.55 ? 1 : 1 - (d - 0.55) * 0.45;
    return { scale: k, alpha: d < 0.6 ? 1 : Math.max(0.35, 1 - (d - 0.6) * 1.6) };
  }

  /** Chip Wear OS : [icon] label / secondaire. */
  function chip(ctx, y, h, { icon, label, secondary, primary = false, width = 186, pressed = 0 } = {}) {
    const e = edge(y + h / 2);
    ctx.save();
    ctx.translate(C, y + h / 2); ctx.scale(e.scale, e.scale); ctx.translate(-C, -(y + h / 2));
    ctx.globalAlpha = e.alpha;
    const x = C - width / 2;
    rrect(ctx, x, y, width, h, h / 2);
    ctx.fillStyle = primary ? COL.orange : COL.surface; ctx.fill();
    if (pressed > 0) { ctx.fillStyle = `rgba(255,255,255,${0.18 * pressed})`; ctx.fill(); }
    const fg = primary ? COL.ink : COL.cream;
    let tx = x + 14;
    if (icon) {
      font(ctx, 15); ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillStyle = fg;
      ctx.fillText(icon, x + 24, y + h / 2 + 1);
      tx = x + 42;
    }
    const tw = width - (tx - x) - 14;
    if (secondary) {
      text(ctx, label, tx, y + h / 2 - 2, { size: 13, weight: 600, color: fg, align: 'left', maxWidth: tw, alpha: e.alpha });
      text(ctx, secondary, tx, y + h / 2 + 13, { size: 11, weight: 500, color: primary ? 'rgba(31,30,29,0.75)' : COL.variant, align: 'left', maxWidth: tw, alpha: e.alpha });
    } else {
      text(ctx, label, tx, y + h / 2 + 5, { size: 13, weight: 600, color: fg, align: 'left', maxWidth: tw, alpha: e.alpha });
    }
    ctx.restore();
  }

  function statRing(ctx, cx, cy, value, color, icon) {
    const r = 15, sw = 3.6;
    ctx.lineWidth = sw; ctx.lineCap = 'round';
    ctx.strokeStyle = 'rgba(240,238,230,0.15)';
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.stroke();
    if (value > 0) {
      ctx.strokeStyle = value < 25 ? COL.low : color;
      ctx.beginPath(); ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.min(1, value / 100)); ctx.stroke();
    }
    Mascot.drawIcon(ctx, icon, cx, cy, 13);
  }

  function roundButton(ctx, cx, cy, icon, { pressed = 0, enabled = true } = {}) {
    const r = 19 * (1 - pressed * 0.08);
    ctx.fillStyle = COL.surface; ctx.globalAlpha = enabled ? 1 : 0.4;
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill();
    if (pressed > 0) { ctx.fillStyle = `rgba(255,255,255,${0.22 * pressed})`; ctx.fill(); }
    ctx.globalAlpha = 1;
    Mascot.drawIcon(ctx, icon, cx, cy, 19 * (1 - pressed * 0.08), enabled ? 1 : 0.4);
  }

  const MOOD_LINE = {
    NONE: 'Tout va bien !', EAT: 'Miam !', PLAY: 'Youpi !', LOVE: '♥', CELEBRATE: 'Tâche terminée, bravo !',
    ALERT: 'On a besoin de toi !', WAVE: 'Coucou !', SLEEP: 'Zzz…', HUNGRY: "J'ai faim…", SAD: 'On joue ?',
    DIRTY: 'Ça sent pas bon ici…', CLEAN: 'Tout propre !',
  };
  const BUBBLE = { EAT: '🍪', PLAY: '🎾', LOVE: '💖', CELEBRATE: '🎉', ALERT: '❗', WAVE: '👋', SLEEP: '💤', HUNGRY: '🍽️', DIRTY: '🤢', CLEAN: '✨' };
  const FACE = { EAT: 'HAPPY', PLAY: 'HAPPY', LOVE: 'HAPPY', CELEBRATE: 'HAPPY', WAVE: 'HAPPY', ALERT: 'WIDE', SLEEP: 'CLOSED', HUNGRY: 'OPEN', SAD: 'SAD', DIRTY: 'SAD', CLEAN: 'HAPPY', NONE: 'HAPPY' };

  /**
   * Écran principal de la mascotte. st : { look, reaction, name, level, satiety, energy, happiness,
   * bounce, jump, blink, poops (0..3), scroll (dp, défilement de la liste), pressed: { feed, play, sleep, clean } (0..1), dim (0..1) }.
   */
  function pet(ctx, st) {
    frame(ctx, st.time);
    const r = st.reaction || 'NONE';
    const sleeping = r === 'SLEEP';
    const excited = ['CELEBRATE', 'PLAY', 'WAVE', 'ALERT'].includes(r);
    const face = st.blink && !sleeping && r !== 'ALERT' ? 'CLOSED' : FACE[r];
    const bounce = st.bounce ?? 0.5;
    ctx.save();
    if (st.scroll) ctx.translate(0, -st.scroll);
    Mascot.drawInBox(ctx, st.look, face, C - 58, 18, 116, 86, {
      bounce, jump: st.jump || 0, excited, altLegs: excited && bounce > 0.5,
      stretch: sleeping ? 1 + bounce * 0.04 : 1, bubble: st.bubble === undefined ? BUBBLE[r] : st.bubble, poops: st.poops || 0,
    });
    text(ctx, `${st.name || 'Chi'} · niv. ${st.level || 3}`, C, 116, { size: 15, weight: 600, color: COL.orange });
    text(ctx, st.moodLine || MOOD_LINE[r], C, 131, { size: 11, weight: 500 });
    statRing(ctx, C - 40, 154, st.satiety ?? 72, COL.satiety, 'SATIETY');
    statRing(ctx, C, 154, st.energy ?? 64, COL.energy, 'ENERGY');
    statRing(ctx, C + 40, 154, st.happiness ?? 86, COL.happiness, 'HAPPINESS');
    const p = st.pressed || {};
    roundButton(ctx, C - 44, 194, 'FEED', { pressed: p.feed || 0, enabled: !sleeping });
    roundButton(ctx, C, 194, 'PLAY', { pressed: p.play || 0, enabled: !sleeping });
    roundButton(ctx, C + 44, 194, sleeping ? 'WAKE' : 'SLEEP', { pressed: p.sleep || 0 });
    if (st.poops) cleanChip(ctx, st.poops, p.clean || 0);
    ctx.restore();
    if (st.scroll) {
      // La liste passe sous l'heure, qui reste en haut (TimeText).
      const fade = ctx.createLinearGradient(0, 0, 0, 30);
      fade.addColorStop(0, '#000'); fade.addColorStop(0.6, '#000'); fade.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = fade; ctx.fillRect(0, 0, D, 30);
      text(ctx, st.time || '10:09', C, 17, { size: 11, weight: 600 });
    }
    if (st.dim) { ctx.fillStyle = `rgba(0,0,0,${0.45 * st.dim})`; ctx.fillRect(0, 0, D, D); }
  }

  /** CompactChip « Nettoyer » sous les actions, quand des cacas traînent (PetScreen). */
  function cleanChip(ctx, poops, pressed) {
    const w = 124, h = 30, x = C - w / 2, y = 222;
    rrect(ctx, x, y, w, h, h / 2); ctx.fillStyle = COL.surface; ctx.fill();
    if (pressed > 0) { ctx.fillStyle = `rgba(255,255,255,${0.18 * pressed})`; ctx.fill(); }
    Mascot.drawIcon(ctx, 'CLEAN', x + 20, y + h / 2, 15);
    text(ctx, poops > 1 ? `Nettoyer (${poops})` : 'Nettoyer', x + 34, y + h / 2 + 4.5, { size: 12, weight: 600, align: 'left' });
  }

  /** Paramètres › Mascotte, avec l'aperçu en direct. st : { look, name, level, highlight: 'species'|'palette'|'accessory', pressed, bounce } */
  function mascotSettings(ctx, st) {
    frame(ctx, st.time);
    text(ctx, 'Mascotte', C, 31, { size: 12, weight: 600, color: COL.variant });
    Mascot.drawInBox(ctx, st.look, st.face || 'HAPPY', C - 44, 33, 88, 62, { bounce: st.bounce ?? 0.5, jump: st.jump || 0 });
    const level = st.level ?? 5;
    const locked = Object.values(Mascot.ACCESSORIES).filter(a => a.level > level).length;
    const items = [
      ['species', '🧬', 'Espèce', Mascot.SPECIES[st.look.species].label],
      ['palette', '🎨', 'Couleurs', Mascot.PALETTES[st.look.palette].label],
      ['accessory', '🎩', 'Accessoire', Mascot.ACCESSORIES[st.look.accessory || 'NONE'].label + (locked ? ` · ${locked} à débloquer` : '')],
    ];
    items.forEach(([key, icon, label, value], i) => {
      chip(ctx, 97 + i * 43, 40, { icon, label, secondary: value, width: 180, pressed: st.highlight === key ? (st.pressed ?? 0) : 0 });
    });
  }

  /** Demande d'autorisation (AskActivity, PERMISSION). */
  function permission(ctx, st = {}) {
    frame(ctx, st.time);
    text(ctx, "🔐 Demande d'autorisation", C, 42, { size: 11.5, weight: 600, color: COL.orange });
    text(ctx, 'Claude · agentchi · ⏱ 1:42', C, 55, { size: 10, weight: 500, color: COL.variant });
    text(ctx, 'Lancer les tests unitaires ?', C, 74, { size: 12.5, weight: 500 });
    rrect(ctx, C - 82, 82, 164, 26, 10); ctx.fillStyle = COL.surface; ctx.fill();
    ctx.font = `500 11px "JetBrains Mono", monospace`; ctx.fillStyle = COL.cream; ctx.textAlign = 'center';
    ctx.fillText('./gradlew :app:test', C, 99);
    chip(ctx, 114, 38, { icon: '✅', label: 'Autoriser', primary: true, width: 184, pressed: st.pressed || 0 });
    chip(ctx, 156, 44, { icon: '♾️', label: 'Toujours autoriser', secondary: 'Ne plus demander pour ça', width: 184 });
    chip(ctx, 204, 38, { icon: '❌', label: 'Refuser', width: 184 });
  }

  function card(ctx, y, h, title, time, lines) {
    const e = edge(y + h / 2);
    ctx.save();
    ctx.translate(C, y + h / 2); ctx.scale(e.scale, e.scale); ctx.translate(-C, -(y + h / 2));
    rrect(ctx, C - 92, y, 184, h, 22); ctx.fillStyle = COL.surface; ctx.globalAlpha = e.alpha; ctx.fill();
    text(ctx, title, C - 78, y + 20, { size: 12.5, weight: 600, align: 'left', alpha: e.alpha, maxWidth: 134 });
    text(ctx, time, C + 78, y + 20, { size: 10, color: COL.variant, align: 'right', alpha: e.alpha });
    lines.forEach(([s, color, size], i) => text(ctx, s, C - 78, y + 36 + i * 14, { size: size || 10.5, color: color || COL.cream, align: 'left', alpha: e.alpha, maxWidth: 156 }));
    ctx.restore();
  }

  function sessions(ctx, st = {}) {
    frame(ctx, st.time);
    text(ctx, 'Sessions en cours', C, 36, { size: 12, weight: 600, color: COL.variant });
    card(ctx, 46, 82, '🔔 Attend ta réponse', '2 min', [
      ['Claude · agentchi', COL.variant, 10], ['⌚ Toucher pour répondre', COL.orange, 10], ['Je peux lancer les tests ?', COL.cream, 11],
    ]);
    card(ctx, 134, 68, '⚙️ Au travail', '6 min', [['☁️ Claude · site-vitrine', COL.variant, 10], ['Refonte de la page d’accueil…', COL.cream, 11]]);
    card(ctx, 208, 60, '✅ À toi de jouer', '14 min', [['Codex · api-go', COL.variant, 10]]);
  }

  function achievements(ctx, st = {}) {
    frame(ctx, st.time);
    text(ctx, 'Succès', C, 34, { size: 12, weight: 600, color: COL.variant });
    text(ctx, "🔥 5 jours d'affilée", C, 53, { size: 15, weight: 600, color: COL.orange });
    text(ctx, 'Record : 12 j · 7 sur 18 succès', C, 67, { size: 10, color: COL.variant });
    chip(ctx, 76, 44, { icon: '🛠️', label: 'Bon début', secondary: '10 tâches terminées', primary: true });
    chip(ctx, 124, 44, { icon: '✅', label: 'Feu vert', secondary: 'Première autorisation', primary: true });
    chip(ctx, 172, 44, { icon: '🔒', label: 'Centurion', secondary: '42 / 100 · 100 tâches' });
  }

  /** Tuile : mascotte, humeur et jauges ; ou question en attente. */
  function tile(ctx, st) {
    frame(ctx, st.time);
    Mascot.drawInBox(ctx, st.look, 'WIDE', C - 40, 22, 80, 62, { bounce: 0.6, excited: true, bubble: '❗' });
    text(ctx, "🔐 Demande d'autorisation", C, 100, { size: 12, weight: 600, color: COL.orange });
    text(ctx, 'Lancer les tests', C, 118, { size: 12 });
    text(ctx, 'unitaires ?', C, 133, { size: 12 });
    rrect(ctx, C - 50, 146, 100, 34, 17); ctx.fillStyle = COL.orange; ctx.fill();
    text(ctx, 'Répondre', C, 168, { size: 13, weight: 700, color: COL.ink });
    text(ctx, 'Claude · agentchi', C, 200, { size: 10, color: COL.variant });
  }

  global.Screens = { D, C, COL, FONT, text, chip, rrect, pet, mascotSettings, permission, sessions, achievements, tile, statRing, roundButton, MOOD_LINE, BUBBLE, FACE };
})(window);
