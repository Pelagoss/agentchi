// Portage JS de app/.../pet/MascotSprite.kt et PixelIcon.kt : mêmes grilles, mêmes couleurs.
// Sert aux captures, visuels et à la vidéo du dossier Stores. Garder synchronisé avec le Kotlin.
(function (global) {
  const WIDTH = 12, HEIGHT = 10, TOP = 2;

  const INK_DARK = '#1F1E1D', TEAR = '#7FB3FF', GOLD = '#F2C14E', PINK = '#F28AB2', PINK_DARK = '#C25A7E',
    CYAN = '#6FD3E0', HAT = '#5A524C', RED = '#D9534F', SHADES = '#111111', WHITE = '#F0EEE6';

  const PALETTES = {
    CORAL: { label: 'Corail', body: '#D97757', accent: '#B4593B', eye: INK_DARK },
    INK: { label: 'Encre', body: '#F0EEE6', accent: '#B9B5AA', eye: INK_DARK },
    OCEAN: { label: 'Océan', body: '#5B8DEF', accent: '#3D6AC4', eye: INK_DARK },
    MINT: { label: 'Menthe', body: '#5DBB7A', accent: '#3E8E58', eye: INK_DARK },
    LILAC: { label: 'Lilas', body: '#A77BE0', accent: '#7E55B8', eye: INK_DARK },
    ROSE: { label: 'Rose', body: '#E97CA0', accent: '#C25A7E', eye: INK_DARK },
  };

  const SPECIES = {
    CRAB: {
      label: 'Crabe',
      body: ['..########..', '..########..', '..########..', '++########++', '++########++', '..########..', '..########..'],
      legsA: '..+.+..+.+..', legsB: '...+.+..+.+.',
      leftEye: 3, rightEye: 8, eyeRow: 1, round: false, defaultPalette: 'CORAL',
    },
    DOTS: {
      label: 'Points',
      body: ['...######...', '..########..', '.##########.', '.##########.', '.##########.', '..########..', '...######...'],
      legsA: '....+..+....', legsB: '...+....+...',
      leftEye: 3, rightEye: 8, eyeRow: 2, round: true, defaultPalette: 'INK',
    },
    GHOST: {
      label: 'Fantôme',
      body: ['...######...', '..########..', '.##########.', '.##########.', '.##########.', '.##########.', '.##########.'],
      legsA: '.+.++..++.+.', legsB: '.++.++.++.+.',
      leftEye: 3, rightEye: 8, eyeRow: 2, round: false, defaultPalette: 'LILAC',
    },
  };

  const ACCESSORIES = {
    NONE: { label: 'Aucun', level: 1 },
    PARTY_HAT: { label: 'Chapeau de fête', level: 1 },
    BOW: { label: 'Nœud', level: 1 },
    SUNGLASSES: { label: 'Lunettes de soleil', level: 2 },
    TOP_HAT: { label: 'Haut-de-forme', level: 3 },
    CROWN: { label: 'Couronne', level: 5 },
  };

  const P = (x, y, c, h = 1, dy = 0, round = false) => ({ x, y, c, h, dy, round });

  function eyes(s, p, face) {
    const l = s.leftEye, r = s.rightEye, y = TOP + s.eyeRow, e = p.eye;
    switch (face) {
      case 'OPEN': return [l, r].flatMap(x => [P(x, y, e), P(x, y + 1, e)]);
      case 'CLOSED': return [l, r].map(x => P(x, y + 1, e, 0.35, 0.4));
      case 'HAPPY': return [l, r].flatMap(x => [P(x - 1, y + 1, e, 0.5, 0.25), P(x, y, e, 0.5, 0.25), P(x + 1, y + 1, e, 0.5, 0.25)]);
      case 'SAD': return [P(l, y + 1, e), P(r, y + 1, e), P(l, y + 2, TEAR, 0.6)];
      case 'WIDE': return [l, l + 1, r - 1, r].flatMap(x => [P(x, y, e), P(x, y + 1, e)])
        .concat([P(5, y + 3, e, 0.6), P(6, y + 3, e, 0.6)]);
    }
    return [];
  }

  function accessory(s, a) {
    const head = s.body[0];
    const left = head.search(/[^.]/), right = head.length - 1 - [...head].reverse().join('').search(/[^.]/);
    const mid = Math.floor((left + right) / 2);
    const row = (y, from, to, c) => { const o = []; for (let x = from; x <= to; x++) o.push(P(x, y, c)); return o; };
    switch (a) {
      case 'PARTY_HAT': return [P(mid, 0, GOLD), P(mid + 1, 0, GOLD), P(mid - 1, 1, PINK), P(mid, 1, CYAN), P(mid + 1, 1, PINK), P(mid + 2, 1, CYAN)];
      case 'BOW': return [P(right - 2, 1, PINK), P(right - 1, 1, PINK_DARK), P(right, 1, PINK), P(right - 2, 0, PINK), P(right, 0, PINK)];
      case 'TOP_HAT': return row(0, mid - 1, mid + 2, HAT).concat(row(1, mid - 2, mid + 3, HAT), [P(mid - 1, 1, RED, 0.4, 0.3)]);
      case 'CROWN': return [P(mid - 2, 0, GOLD), P(mid, 0, GOLD), P(mid + 1, 0, GOLD), P(mid + 3, 0, GOLD)]
        .concat(row(1, mid - 2, mid + 3, GOLD), [P(mid, 1, RED, 0.5, 0.25)]);
      case 'SUNGLASSES': {
        const y = TOP + s.eyeRow;
        return [s.leftEye - 1, s.leftEye, s.leftEye + 1, s.rightEye - 1, s.rightEye, s.rightEye + 1]
          .flatMap(x => [P(x, y, SHADES), P(x, y + 1, SHADES, 0.7)])
          .concat(row(y, s.leftEye + 2, s.rightEye - 2, SHADES).map(p => ({ ...p, h: 0.4 })))
          .concat([P(s.leftEye - 1, y, WHITE, 0.35), P(s.rightEye - 1, y, WHITE, 0.35)]);
      }
    }
    return [];
  }

  /** look = { species, palette, accessory } (clés des tables ci-dessus). */
  function pixels(look, face = 'OPEN', altLegs = false, stretch = 1) {
    const s = SPECIES[look.species], p = PALETTES[look.palette || s.defaultPalette];
    const out = [];
    s.body.forEach((row, y) => [...row].forEach((c, x) => {
      if (c === '#') out.push(P(x, TOP + y, p.body, stretch, 0, s.round));
      if (c === '+') out.push(P(x, TOP + y, p.accent, stretch, 0, s.round));
    }));
    [...(altLegs ? s.legsB : s.legsA)].forEach((c, x) => { if (c !== '.') out.push(P(x, TOP + 7, p.accent, 1, 0, s.round)); });
    return out.concat(eyes(s, p, face), accessory(s, look.accessory || 'NONE'));
  }

  /** Dessine la grille 12 x 10 avec son coin haut-gauche en (x, y), une case = px. [filter] : (pixel, index) -> {alpha, ox, oy} */
  function draw(ctx, look, face, x, y, px, opts = {}) {
    const list = pixels(look, face, opts.altLegs, opts.stretch || 1);
    list.forEach((p, i) => {
      const f = opts.filter ? opts.filter(p, i, list.length) : null;
      if (f && f.alpha <= 0) return;
      ctx.globalAlpha = (opts.alpha ?? 1) * (f ? f.alpha : 1);
      ctx.fillStyle = opts.tint || p.c;
      const ox = f?.ox || 0, oy = f?.oy || 0;
      const top = y + (p.y + p.dy) * px + oy;
      if (p.round) {
        ctx.beginPath();
        ctx.arc(x + (p.x + 0.5) * px + ox, top + px * p.h / 2, px * 0.46, 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.fillRect(x + p.x * px + ox, top, px + 0.5, px * p.h + 0.5);
      }
    });
    ctx.globalAlpha = 1;
  }

  /** Comme Mascot.kt : dans une boîte (bx, by, w, h), centrée, avec ombre, rebond [bounce] 0..1 et saut [jump] 0..1. */
  function drawInBox(ctx, look, face, bx, by, w, h, o = {}) {
    const px = Math.min(w / (WIDTH + 2), h / (HEIGHT + 3));
    const bounce = o.bounce || 0, jump = o.jump || 0;
    const amplitude = o.excited ? px * 1.2 : px * 0.35;
    const ox = bx + (w - WIDTH * px) / 2;
    const oy = by + (h - HEIGHT * px) / 2 - bounce * amplitude - jump * px * 2.5;
    const shadowW = WIDTH * px * (0.75 - bounce * 0.1);
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    ctx.beginPath();
    ctx.ellipse(bx + w / 2, by + (h + HEIGHT * px) / 2 + px * 0.2 + px * 0.4, shadowW / 2, px * 0.4, 0, 0, Math.PI * 2);
    ctx.fill();
    draw(ctx, look, face, ox, oy, px, { altLegs: o.altLegs, stretch: o.stretch, filter: o.filter, alpha: o.alpha });
    if (o.bubble) {
      ctx.font = `${Math.round(px * 2.9)}px "Noto Color Emoji"`;
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(o.bubble, bx + w - px * 1.9, by + px * 2.2 - bounce * px * 0.6);
    }
    return px;
  }

  const ICONS = {
    FEED: { a: '#E8B04A', b: '#8B5A2B', grid: ['..aaaa..', '.aaaaba.', 'aabaaaaa', 'aaaaaaba', 'abaaaaaa', 'aaaabaaa', '.aaaaaa.', '..aaaa..'] },
    PLAY: { a: '#C8E64C', b: '#F0EEE6', grid: ['..aaaa..', '.baaaab.', 'aabaabaa', 'aaabbaaa', 'aaabbaaa', 'aabaabaa', '.baaaab.', '..aaaa..'] },
    SLEEP: { a: '#F2C14E', b: '#B9B5AA', grid: ['...aaa..', '..aa...b', '.aa...b.', '.aa..bbb', '.aa.....', '.aaa....', '..aaaaa.', '...aaa..'] },
    WAKE: { a: '#F2C14E', b: '#E8B04A', grid: ['...a....', '.a.a.a..', '..bbb...', 'aabbbaa.', '..bbb...', '.a.a.a..', '...a....', '........'] },
    ENERGY: { a: '#7FB3FF', b: '#7FB3FF', grid: ['....aa..', '...aa...', '..aa....', '.aaaaaa.', '....aa..', '...aa...', '..aa....', '.a......'] },
    HAPPINESS: { a: '#E97CA0', b: '#F0EEE6', grid: ['.aa..aa.', 'abaaaaaa', 'aaaaaaaa', 'aaaaaaaa', '.aaaaaa.', '..aaaa..', '...aa...', '........'] },
    NOTIFICATIONS: { a: '#F2C14E', b: '#B4593B', grid: ['...aa...', '..aaaa..', '.aaaaaa.', '.aaaaaa.', '.aaaaaa.', 'aaaaaaaa', '........', '...bb...'] },
    SETTINGS: { a: '#B0AEA5', b: '#B0AEA5', grid: ['..a..a..', '.aaaaaa.', 'aaa..aaa', '.a....a.', '.a....a.', 'aaa..aaa', '.aaaaaa.', '..a..a..'] },
  };
  ICONS.SATIETY = ICONS.FEED;

  /** Icône 8 x 8 centrée en (cx, cy), de côté size. */
  function drawIcon(ctx, name, cx, cy, size, alpha = 1) {
    const ic = ICONS[name], px = size / 8, x0 = cx - size / 2, y0 = cy - size / 2;
    ctx.globalAlpha = alpha;
    ic.grid.forEach((row, y) => [...row].forEach((c, x) => {
      if (c === '.') return;
      ctx.fillStyle = c === 'a' ? ic.a : ic.b;
      ctx.fillRect(x0 + x * px, y0 + y * px, px + 0.4, px + 0.4);
    }));
    ctx.globalAlpha = 1;
  }

  global.Mascot = { WIDTH, HEIGHT, TOP, PALETTES, SPECIES, ACCESSORIES, ICONS, pixels, draw, drawInBox, drawIcon };
})(window);
