import { useEffect, useRef } from 'react';
import { effectArtwork } from './EffectArtwork';
export function Motion({ effect, color, dream = false }: { effect: string; color: string; dream?: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current!,
      ctx = canvas.getContext('2d')!;
    let frame = 0,
      previous = 0,
      started = performance.now();
    const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
    const artwork = effectArtwork(ctx);
    const initialDock = canvas.parentElement?.querySelector<HTMLElement>('.mock-dock');
    let dockTop = initialDock && canvas.clientHeight ? initialDock.offsetTop / canvas.clientHeight : 0.64;
    const palette = ['#fff3db', color, '#d7edff', '#ffd5df'];
    const glow = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
    glow.addColorStop(0, '#fff5deaa');
    glow.addColorStop(0.2, color + '65');
    glow.addColorStop(1, color + '00');
    const sparkle = new Path2D();
    sparkle.moveTo(0, -1);
    sparkle.quadraticCurveTo(0.12, -0.12, 1, 0);
    sparkle.quadraticCurveTo(0.12, 0.12, 0, 1);
    sparkle.quadraticCurveTo(-0.12, 0.12, -1, 0);
    sparkle.quadraticCurveTo(-0.12, -0.12, 0, -1);
    const petal = new Path2D();
    petal.moveTo(-1, 0);
    petal.bezierCurveTo(-0.35, -1, 0.7, -0.85, 1, 0);
    petal.bezierCurveTo(0.35, 1, -0.7, 0.85, -1, 0);
    const bloomPetal = new Path2D();
    bloomPetal.moveTo(0, -0.04);
    bloomPetal.bezierCurveTo(-0.62, -0.35, -0.55, -1.24, 0, -1.05);
    bloomPetal.bezierCurveTo(0.55, -1.24, 0.62, -0.35, 0, -0.04);
    bloomPetal.closePath();
    const bloomCounts = [6, 8, 10];
    const blooms = bloomCounts.map((count) => {
      const path = new Path2D();
      for (let i = 0; i < count; i++) path.addPath(bloomPetal, new DOMMatrix().rotate((i * 360) / count));
      return path;
    });
    const draw = (time: number) => {
      if (!reduced && effect !== 'none') frame = requestAnimationFrame(draw);
      if (document.hidden || time - previous < 33) return;
      previous = time;
      const ratio = Math.min(devicePixelRatio, 2);
      const w = Math.round(canvas.clientWidth * ratio),
        h = Math.round(canvas.clientHeight * ratio);
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
        const dock = canvas.parentElement?.querySelector<HTMLElement>('.mock-dock');
        dockTop = dock ? dock.offsetTop / canvas.clientHeight : 0.64;
      }
      ctx.clearRect(0, 0, w, h);
      if (effect === 'none') return;
      const t = reduced ? 12 : (time - started) / 1000;
      if (artwork(effect, t, w, h, reduced, dream ? h * 0.98 : h * dockTop - (10 * w) / 960)) return;
      const flowers = effect === 'flowers' || effect === 'bubbles';
      const steady = flowers ? 36 : effect === 'petals' ? 52 : 60;
      const maximum = flowers ? 52 : effect === 'petals' ? 80 : 96;
      const count = reduced ? 12 : t < 10 ? maximum : steady;
      const unit = w / 960;
      for (let i = 0; i < count; i++) {
        const seed = (i * 0.6180339887) % 1,
          depth = 0.35 + (i % 3) * 0.325;
        const arrival = i < steady ? 1 : Math.max(0, Math.min(1, (10 - t) / 4));
        const speed = flowers ? 0.008 + depth * 0.018 : 0.012 + depth * 0.021;
        const x =
          ((seed + Math.sin(t * 0.25 + i) * (0.025 + depth * 0.035) + Math.sin(t * 0.11) * 0.025 + 2) % 1) *
          w;
        const y = ((i * 0.173205 + t * speed + 10) % 1) * h;
        const radius = (flowers ? 13 + seed * 16 : 3.5 + seed * 8) * unit * depth;
        const twinkle = 0.5 + 0.5 * Math.sin(t * (1.3 + seed) + i * 1.7);
        const alpha = arrival * (0.38 + depth * 0.42) * (effect === 'sparkles' ? 0.25 + twinkle * 0.75 : 1);
        ctx.save();
        ctx.translate(x, y);
        if (effect === 'sparkles' && i % 4 === 0) {
          ctx.save();
          ctx.scale(radius * 4.4, radius * 4.4);
          ctx.globalAlpha = alpha * 0.35;
          ctx.fillStyle = glow;
          ctx.beginPath();
          ctx.arc(0, 0, 1, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }
        ctx.rotate(t * (flowers ? 0.12 + depth * 0.1 : 0.3 + depth * 0.25) + i);
        ctx.globalAlpha = alpha;
        ctx.fillStyle = palette[i % palette.length];
        ctx.strokeStyle = palette[i % palette.length];
        ctx.lineWidth = Math.max(0.5, unit);
        if (flowers) {
          ctx.scale(radius, radius * (0.85 + 0.15 * Math.sin(t * 0.65 + i)));
          const bloom = blooms[i % blooms.length];
          ctx.fill(bloom);
          ctx.save();
          ctx.rotate(Math.PI / bloomCounts[i % 3]);
          ctx.scale(0.62, 0.62);
          ctx.fillStyle = '#fff3e0';
          ctx.globalAlpha = alpha * 0.65;
          ctx.fill(bloom);
          ctx.restore();
          ctx.fillStyle = '#e9b85d';
          ctx.globalAlpha = alpha;
          ctx.beginPath();
          ctx.arc(0, 0, 0.23, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = '#fff6d7';
          ctx.globalAlpha = alpha * 0.8;
          ctx.beginPath();
          ctx.arc(-0.06, -0.06, 0.08, 0, Math.PI * 2);
          ctx.fill();
        } else if (effect === 'confetti') {
          const flutter = 0.22 + 0.78 * Math.abs(Math.cos(t * 1.5 + i));
          ctx.scale(flutter, 1);
          if (i % 4 === 0) {
            ctx.lineWidth = radius * 0.3;
            ctx.beginPath();
            ctx.moveTo(-radius * 1.5, -radius * 0.3);
            ctx.bezierCurveTo(-radius * 0.5, -radius, radius * 0.5, radius, radius * 1.5, radius * 0.3);
            ctx.stroke();
          } else {
            ctx.fillRect(-radius, -radius * 0.3, radius * 2, radius * 0.6);
            ctx.globalAlpha = alpha * 0.45;
            ctx.fillStyle = '#fff5df';
            ctx.fillRect(-radius, -radius * 0.3, radius * 2, radius * 0.12);
          }
        } else if (effect === 'petals') {
          ctx.scale(radius * (0.35 + 0.65 * Math.abs(Math.cos(t * 0.8 + i))), radius * 0.85);
          ctx.fill(petal);
          ctx.globalAlpha = alpha * 0.5;
          ctx.strokeStyle = '#fff5e4';
          ctx.lineWidth = 0.07;
          ctx.beginPath();
          ctx.moveTo(-0.65, 0);
          ctx.quadraticCurveTo(0, -0.15, 0.65, 0);
          ctx.stroke();
        } else {
          ctx.scale(radius, radius);
          ctx.fill(sparkle);
          ctx.globalAlpha = alpha * 0.85;
          ctx.fillStyle = '#fff9ed';
          ctx.beginPath();
          ctx.arc(0, 0, 0.13, 0, Math.PI * 2);
          ctx.fill();
          if (i % 7 === 0) {
            ctx.strokeStyle = '#fff5de';
            ctx.lineWidth = 0.06;
            ctx.globalAlpha = alpha * 0.5;
            ctx.beginPath();
            ctx.moveTo(-1.8, 0);
            ctx.lineTo(1.8, 0);
            ctx.moveTo(0, -1.8);
            ctx.lineTo(0, 1.8);
            ctx.stroke();
          }
        }
        ctx.restore();
      }
    };
    frame = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(frame);
  }, [effect, color, dream]);
  return <canvas className="motion" data-effect={effect} ref={ref} aria-hidden="true" />;
}
