// Montre ronde générique (boîtier, lunette, deux boutons, bracelet) et décors partagés par les visuels et la vidéo.
(function (global) {
  /** Dessine une montre centrée en (cx, cy) dont l'écran a le rayon R ; drawScreen(ctx) dessine en dp (225 x 225). */
  function watch(ctx, cx, cy, R, drawScreen, { strap = true, tilt = 0, glow = 0 } = {}) {
    ctx.save();
    ctx.translate(cx, cy); ctx.rotate(tilt);
    const caseR = R * 1.2;
    if (strap) {
      const sw = R * 1.35;
      for (const dir of [-1, 1]) {
        const g = ctx.createLinearGradient(0, dir * caseR * 0.6, 0, dir * R * 2.4);
        g.addColorStop(0, '#2a2725'); g.addColorStop(1, 'rgba(30,28,27,0)');
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.roundRect(-sw / 2, dir > 0 ? caseR * 0.55 : -R * 2.4, sw, R * 2.4 - caseR * 0.55, R * 0.18);
        ctx.fill();
      }
    }
    if (glow) {
      const g = ctx.createRadialGradient(0, 0, R * 0.6, 0, 0, R * 2.2);
      g.addColorStop(0, `rgba(217,119,87,${0.28 * glow})`); g.addColorStop(1, 'rgba(217,119,87,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, R * 2.2, 0, Math.PI * 2); ctx.fill();
    }
    // Boutons latéraux
    ctx.fillStyle = '#3b3734';
    for (const a of [-0.42, 0.42]) {
      ctx.save(); ctx.rotate(a);
      ctx.beginPath(); ctx.roundRect(caseR - R * 0.05, -R * 0.1, R * 0.14, R * 0.2, R * 0.05); ctx.fill();
      ctx.restore();
    }
    // Boîtier
    const cg = ctx.createLinearGradient(-caseR, -caseR, caseR, caseR);
    cg.addColorStop(0, '#5a5551'); cg.addColorStop(0.5, '#2e2b29'); cg.addColorStop(1, '#4a4542');
    ctx.fillStyle = cg; ctx.beginPath(); ctx.arc(0, 0, caseR, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#0b0b0b'; ctx.beginPath(); ctx.arc(0, 0, R * 1.08, 0, Math.PI * 2); ctx.fill();
    // Écran
    ctx.save();
    ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.clip();
    const s = (2 * R) / Screens.D;
    ctx.translate(-R, -R); ctx.scale(s, s);
    drawScreen(ctx);
    ctx.restore();
    // Reflet
    const rg = ctx.createLinearGradient(-R, -R, R * 0.2, R * 0.2);
    rg.addColorStop(0, 'rgba(255,255,255,0.07)'); rg.addColorStop(0.5, 'rgba(255,255,255,0)');
    ctx.fillStyle = rg; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
  }

  /** Fond encre, halo chaud et trame de points pixel-art. */
  function backdrop(ctx, w, h, { t = 0, hue = '#D97757', fx = 0.7, fy = 0.45, dots = true } = {}) {
    ctx.fillStyle = '#161514'; ctx.fillRect(0, 0, w, h);
    const g = ctx.createRadialGradient(w * fx, h * fy, 0, w * fx, h * fy, Math.max(w, h) * 0.75);
    g.addColorStop(0, hexA(hue, 0.30)); g.addColorStop(0.45, hexA(hue, 0.08)); g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    if (dots) {
      const step = Math.round(Math.min(w, h) / 27);
      ctx.fillStyle = 'rgba(240,238,230,0.045)';
      for (let y = step / 2; y < h; y += step) for (let x = step / 2; x < w; x += step) {
        const k = 0.5 + 0.5 * Math.sin(x * 0.013 + y * 0.011 + t * 1.3);
        const s = step * (0.08 + 0.07 * k);
        ctx.fillRect(x - s / 2, y - s / 2, s, s);
      }
    }
  }

  function hexA(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`;
  }

  /** Texte multi-lignes centré ou aligné, police d'affichage. */
  function title(ctx, lines, x, y, { size = 80, weight = 800, color = '#F0EEE6', align = 'center', lh = 1.08, accent, family = '"Inter Display", "Inter", "Noto Color Emoji"' } = {}) {
    ctx.textAlign = align; ctx.textBaseline = 'alphabetic';
    ctx.font = `${weight} ${size}px ${family}`;
    lines.forEach((l, i) => {
      ctx.fillStyle = accent && accent.includes(i) ? '#D97757' : color;
      ctx.fillText(l, x, y + i * size * lh);
    });
  }

  global.Deco = { watch, backdrop, hexA, title };
})(window);
