import { peacockFeather } from './PeacockFeather';
/** Small cached vector overlays: no video downloads, image sequences or full-screen blur. */
export function effectArtwork(ctx: CanvasRenderingContext2D) {
  const petal = new Path2D('M-1 0 C-.35-1 .7-.85 1 0 C.35 1-.7 .85-1 0Z');
  const leaf = new Path2D(
    'M0 1.5 C-.15 .65-1.2 .4-.8-.4 C-.45-1.1 0-.65 0-.45 C0-.65 .45-1.1 .8-.4 C1.2 .4 .15 .65 0 1.5Z',
  );
  const bell = new Path2D('M-1 .5 Q-.65 .2-.6-.5 Q-.55-1.2 0-1.2 Q.55-1.2 .6-.5 Q.65 .2 1 .5 Q0 .9-1 .5Z');
  const flame = new Path2D('M0-1.6 C-.1-.5-1-.2-.6 .5 C-.3 1.2 .7 1 .7 .2 C.7-.45 .1-.65 0-1.6Z');
  let feather: ReturnType<typeof peacockFeather> | undefined;
  const star = new Path2D('M0-1 Q.1-.1 1 0 Q.1 .1 0 1 Q-.1 .1-1 0 Q-.1-.1 0-1Z');
  const blossom = new Path2D();
  const veins = new Path2D('M0-.45 L0 1.3');
  for (let i = 0; i < 4; i++) {
    const y = -0.2 + i * 0.24,
      span = 0.55 - i * 0.1;
    veins.moveTo(0, y + 0.22);
    veins.quadraticCurveTo(-span * 0.6, y, -span, y - 0.15);
    veins.moveTo(0, y + 0.22);
    veins.quadraticCurveTo(span * 0.6, y, span, y - 0.15);
  }
  for (let i = 0; i < 12; i++) {
    const p = new Path2D('M0 0 C-.6-.3-.5-1.1 0-1 C.5-1.1 .6-.3 0 0Z');
    blossom.addPath(p, new DOMMatrix().rotate(i * 30));
  }
  const gradient = (color: string) => {
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
    g.addColorStop(0, color + 'bb');
    g.addColorStop(0.38, color + '55');
    g.addColorStop(1, color + '00');
    return g;
  };
  const gold = gradient('#ffc46a');
  const fire = ctx.createLinearGradient(0, -1.6, 0, 1);
  fire.addColorStop(0, '#fff0a0');
  fire.addColorStop(0.38, '#ffd65b');
  fire.addColorStop(0.75, '#ff9b25');
  fire.addColorStop(1, '#eb6630');
  const brass = ctx.createLinearGradient(-1, 0, 1, 0);
  brass.addColorStop(0, '#a56b2b');
  brass.addColorStop(0.35, '#f0c46a');
  brass.addColorStop(0.65, '#c08c3e');
  brass.addColorStop(1, '#765125');
  const powderColors = ['#ed358b', '#22c6cf', '#ff841f', '#ffd736'];
  const clouds = powderColors.map(gradient);
  const halo = (x: number, y: number, rx: number, ry: number, paint: CanvasGradient, alpha: number) => {
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(rx, ry);
    ctx.globalAlpha = alpha;
    ctx.fillStyle = paint;
    ctx.beginPath();
    ctx.arc(0, 0, 1, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  };
  return (effect: string, t: number, w: number, h: number, reduced: boolean, safeBottom = h * 0.62) => {
    if (
      !['diya', 'gulal', 'temple', 'marigold', 'mantra', 'starlight', 'cosmic', 'peacock', 'leaves'].includes(
        effect,
      )
    )
      return false;
    const u = w / 960,
      pulse = 0.5 + 0.5 * Math.sin(t * 0.6);
    if (effect === 'gulal') {
      for (let i = 0; i < 5; i++)
        halo(
          w * (0.1 + i * 0.21 + Math.sin(t * 0.08 + i) * 0.12),
          h * (0.3 + Math.sin(t * 0.12 + i * 1.8) * 0.3),
          w * 0.37,
          h * (0.32 + (i % 2) * 0.16),
          clouds[i % 4],
          0.52 + 0.08 * Math.sin(t * 0.18 + i),
        );
      for (let i = 0; i < 24; i++) {
        ctx.globalAlpha = 0.12 + 0.16 * (0.5 + 0.5 * Math.sin(t * 0.8 + i));
        ctx.fillStyle = powderColors[i % 4];
        ctx.beginPath();
        ctx.arc(
          w * ((i * 0.618 + Math.sin(t * 0.1 + i) * 0.06 + 2) % 1),
          h * ((i * 0.173 + t * 0.005) % 1),
          (0.5 + (i % 3) * 0.4) * u,
          0,
          Math.PI * 2,
        );
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      return true;
    }
    if (effect === 'temple' || effect === 'mantra') {
      halo(w * 0.5, h * 0.45, w * (0.33 + pulse * 0.05), h * 0.65, gold, 0.25 + pulse * 0.16);
      if (effect === 'temple')
        for (const x of [0.08, 0.92]) {
          ctx.save();
          ctx.translate(w * x, 0);
          ctx.rotate(Math.sin(t * 0.8 + x) * 0.08);
          ctx.strokeStyle = '#d9a753';
          ctx.lineWidth = 2 * u;
          ctx.globalAlpha = 0.9;
          ctx.beginPath();
          ctx.moveTo(0, 0);
          ctx.lineTo(0, 52 * u);
          ctx.stroke();
          ctx.translate(0, 84 * u);
          ctx.scale(30 * u, 30 * u);
          ctx.fillStyle = brass;
          ctx.fill(bell);
          ctx.strokeStyle = '#ffe0a0';
          ctx.lineWidth = 0.045;
          ctx.stroke(bell);
          ctx.globalAlpha = 0.5;
          ctx.lineWidth = 0.025;
          ctx.beginPath();
          ctx.moveTo(-0.52, -0.75);
          ctx.quadraticCurveTo(0, -0.52, 0.52, -0.75);
          ctx.moveTo(-0.62, -0.22);
          ctx.quadraticCurveTo(0, 0.02, 0.62, -0.22);
          ctx.stroke();
          ctx.globalAlpha = 0.85;
          for (let n = 0; n < 5; n++) {
            ctx.beginPath();
            ctx.arc(-0.5 + n * 0.25, 0.32, 0.035, 0, Math.PI * 2);
            ctx.fillStyle = '#ffe1a1';
            ctx.fill();
          }
          ctx.fillStyle = '#ffe1a1';
          ctx.beginPath();
          ctx.ellipse(0, 0.55, 0.85, 0.14, 0, 0, Math.PI * 2);
          ctx.fill();
          ctx.beginPath();
          ctx.arc(Math.sin(t * 0.8) * 0.12, 0.65, 0.12, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }
      if (effect === 'mantra') {
        ctx.save();
        ctx.globalAlpha = 0.34 + pulse * 0.3;
        ctx.fillStyle = '#ffe1a0';
        ctx.font = `${Math.round(72 * u)}px "Nirmala UI", "Noto Sans Devanagari", sans-serif`;
        ctx.textAlign = 'center';
        ctx.fillText('ॐ', w * 0.5, h * 0.58);
        ctx.translate(w * 0.5, h * 0.5);
        ctx.rotate(t * 0.025);
        ctx.strokeStyle = '#efce8c';
        ctx.lineWidth = u;
        for (let ring = 0; ring < 2; ring++) {
          ctx.globalAlpha = 0.12 + pulse * 0.06;
          ctx.beginPath();
          ctx.ellipse(0, 0, (68 + ring * 14) * u, (60 + ring * 12) * u, 0, 0, Math.PI * 2);
          ctx.stroke();
        }
        ctx.restore();
      }
    }
    if (effect === 'diya')
      for (const x of [0.035, 0.965]) {
        const floor = safeBottom - 24 * u,
          phase = t + x * 5;
        halo(w * x, floor - 30 * u, 90 * u, 100 * u, gold, 0.42 + 0.05 * Math.sin(phase * 7));
        ctx.save();
        ctx.translate(w * x, floor);
        ctx.scale(26 * u, 24 * u);
        ctx.fillStyle = '#a04d2a';
        ctx.beginPath();
        ctx.ellipse(0, 0, 1.2, 0.55, 0, 0, Math.PI);
        ctx.fill();
        ctx.strokeStyle = '#edaa58';
        ctx.lineWidth = 0.08;
        ctx.beginPath();
        ctx.ellipse(0, 0, 1.2, 0.17, 0, 0, Math.PI * 2);
        ctx.stroke();
        ctx.fillStyle = '#c77c38';
        ctx.globalAlpha = 0.8;
        ctx.beginPath();
        ctx.ellipse(0, 0, 1.1, 0.12, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#f3bd68';
        ctx.lineWidth = 0.035;
        for (let n = 0; n < 7; n++) {
          const px = -0.84 + n * 0.28;
          ctx.beginPath();
          ctx.moveTo(px, 0.2);
          ctx.quadraticCurveTo(px + 0.1, 0.46, px + 0.22, 0.17);
          ctx.stroke();
        }
        ctx.globalAlpha = 1;
        ctx.strokeStyle = '#4e3023';
        ctx.lineWidth = 0.1;
        ctx.beginPath();
        ctx.moveTo(-0.05, -0.05);
        ctx.lineTo(0.05, -0.38);
        ctx.stroke();
        ctx.translate(0, -0.78);
        ctx.rotate(Math.sin(phase * 4.2) * 0.045);
        ctx.scale(0.6 + Math.sin(phase * 8) * 0.04, 0.75 + Math.sin(phase * 6) * 0.06);
        ctx.fillStyle = fire;
        ctx.fill(flame);
        ctx.scale(0.45, 0.7);
        ctx.fillStyle = '#fff1b2';
        ctx.fill(flame);
        ctx.restore();
      }
    if (effect === 'cosmic') {
      for (let row = 0; row < 5; row++) {
        ctx.beginPath();
        for (let x = 0; x <= 40; x++) {
          const y = safeBottom - (50 - row * 9) * u + Math.sin(x * 0.38 + t * 0.65 + row) * (3 + row) * u;
          if (!x) ctx.moveTo(0, y);
          else ctx.lineTo((w * x) / 40, y);
        }
        ctx.strokeStyle = ['#62b9c1', '#c3e4de'][row % 2];
        ctx.globalAlpha = 0.16 + row * 0.025;
        ctx.lineWidth = u * 1.4;
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
    }
    if (effect === 'peacock') {
      feather ??= peacockFeather();
      for (const side of [-1, 1]) {
        ctx.save();
        ctx.translate(w * (side < 0 ? 0.06 : 0.94), h * 0.7);
        ctx.rotate(side * (0.27 + Math.sin(t * 0.28) * 0.07));
        ctx.scale(48 * u, 85 * u);
        ctx.globalAlpha = 0.94;
        ctx.drawImage(feather.image, -1.28, -2.24, 2.56, 4.16);
        ctx.lineWidth = 0.008;
        for (let i = 0; i < feather.highlights.length; i++) {
          ctx.strokeStyle = i % 2 ? '#e2ce82' : '#78dece';
          ctx.globalAlpha = (38 + 35 * Math.max(0, Math.sin(t * 0.55 + side - i * 0.8))) / 255;
          ctx.stroke(feather.highlights[i]);
        }
        ctx.restore();
      }
    }
    const rising = ['diya', 'starlight', 'cosmic', 'peacock'].includes(effect);
    const count = reduced
      ? 10
      : effect === 'marigold'
        ? t < 10
          ? 70
          : 46
        : effect === 'leaves'
          ? 42
          : effect === 'diya'
            ? 32
            : effect === 'peacock'
              ? 18
              : 48;
    for (let i = 0; i < count; i++) {
      const seed = (i * 0.6180339887) % 1,
        depth = 0.4 + (i % 3) * 0.3;
      let x = ((seed + Math.sin(t * 0.22 + i) * 0.06 + 2) % 1) * w;
      const phase = (i * 0.173205 + t * (0.008 + depth * 0.02)) % 1;
      let y = (rising ? 1 - phase : phase) * h;
      if (effect === 'diya') {
        x = w * (i % 2 ? 0.965 : 0.035) + Math.sin(i + t * 0.4) * phase * 32 * u;
        y = safeBottom - 50 * u - phase * h * 0.35;
      }
      const radius =
        (effect === 'marigold' || effect === 'leaves'
          ? 10 + seed * 10
          : effect === 'diya'
            ? 1.2 + seed * 2.5
            : effect === 'peacock'
              ? 1 + seed * 2
              : 2 + seed * 4) *
        u *
        depth;
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(t * 0.2 + i);
      ctx.scale(radius, radius);
      ctx.globalAlpha = (0.35 + depth * 0.4) * (0.55 + 0.45 * Math.sin(t * 1.1 + i) ** 2);
      if (effect === 'diya') ctx.globalAlpha *= 1 - phase;
      if (effect === 'marigold') {
        ctx.scale(0.5 + 0.5 * Math.abs(Math.cos(t * 0.7 + i)), 1);
        ctx.fillStyle = ['#ffb920', '#ff791e', '#ba2843'][i % 3];
        ctx.fill(i % 3 === 2 ? petal : blossom);
        if (i % 3 !== 2) {
          ctx.scale(0.6, 0.6);
          ctx.rotate(0.25);
          ctx.fillStyle = '#ffdb64';
          ctx.fill(blossom);
          ctx.rotate(0.2);
          ctx.scale(0.55, 0.55);
          ctx.fillStyle = '#f39124';
          ctx.fill(blossom);
        } else {
          ctx.strokeStyle = '#ff9da1';
          ctx.lineWidth = 0.07;
          ctx.beginPath();
          ctx.moveTo(-0.7, 0);
          ctx.quadraticCurveTo(0, -0.18, 0.65, 0);
          ctx.stroke();
        }
      } else if (effect === 'leaves') {
        ctx.scale(0.5 + 0.5 * Math.abs(Math.cos(t * 0.5 + i)), 1);
        ctx.fillStyle = '#dfbc68';
        ctx.fill(leaf);
        ctx.strokeStyle = '#fff2b6';
        ctx.lineWidth = 0.06;
        ctx.globalAlpha *= 0.65;
        ctx.stroke(veins);
      } else {
        ctx.fillStyle =
          effect === 'peacock' ? (i % 2 ? '#78dece' : '#b1bb95') : i % 3 ? '#ffd68a' : '#fff4dc';
        if (effect === 'diya' && i % 8 !== 0) {
          ctx.beginPath();
          ctx.arc(0, 0, 0.7, 0, Math.PI * 2);
          ctx.fill();
        } else ctx.fill(star);
        if (effect === 'starlight' && i % 3 === 0) {
          ctx.strokeStyle = '#ffe4a6';
          ctx.lineWidth = 0.12;
          ctx.beginPath();
          ctx.moveTo(0, 1);
          ctx.lineTo(0, 4);
          ctx.stroke();
        }
      }
      ctx.restore();
    }
    return true;
  };
}
