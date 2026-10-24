/** A feather is painted once. Animation moves the texture and a few fine iridescent strands. */
export function peacockFeather() {
  const image = document.createElement('canvas');
  image.width = 256;
  image.height = 416;
  const c = image.getContext('2d')!;
  c.translate(128, 224);
  c.scale(100, 100);
  c.lineCap = 'round';
  const highlights = [new Path2D(), new Path2D(), new Path2D()];
  const colors = ['#419e80', '#72b365', '#22a8a1', '#3f9673', '#c4ac57', '#287c9e'];
  // Unequal, swept barbs leave air between strands instead of a solid leaf silhouette.
  for (let i = 0; i < 104; i++) {
    const y = 1.28 - i * 0.0317;
    const envelope = Math.pow(Math.max(0, Math.sin((Math.PI * (y + 2.05)) / 3.35)), 0.65);
    const span = envelope * (0.28 + 0.64 * Math.exp(-(((y + 0.85) / 1.05) ** 2)));
    for (const side of [-1, 1]) {
      const length = span * (1 + 0.055 * Math.sin(i * 2.17 + side));
      const shaft = 0.025 * Math.sin(y * 1.5);
      const tip = side * length;
      const path = new Path2D();
      path.moveTo(shaft, y + 0.15);
      path.bezierCurveTo(
        side * length * 0.24,
        y + 0.07,
        tip * 0.78,
        y - 0.17,
        tip,
        y - 0.31 - 0.035 * Math.sin(i),
      );
      c.strokeStyle = colors[(i + (side > 0 ? 2 : 0)) % colors.length];
      c.globalAlpha = 0.58 + 0.25 * Math.sin(i * 1.31) ** 2;
      c.lineWidth = i % 3 ? 0.006 : 0.009;
      c.stroke(path);
      if (i % 4 === 0) highlights[i % 3].addPath(path);
      if (i % 2 === 0) {
        c.globalAlpha = 0.3;
        c.lineWidth = 0.0035;
        c.strokeStyle = '#d6c27e';
        c.beginPath();
        c.moveTo(tip * 0.66, y - 0.09);
        c.quadraticCurveTo(tip * 0.87, y - 0.18, tip * 1.065, y - 0.31);
        c.stroke();
      }
    }
  }
  const shaft = c.createLinearGradient(0, -2, 0, 1.8);
  shaft.addColorStop(0, '#75875b');
  shaft.addColorStop(0.6, '#c4b984');
  shaft.addColorStop(1, '#ad976b');
  c.globalAlpha = 0.82;
  c.lineWidth = 0.014;
  c.strokeStyle = shaft;
  c.beginPath();
  c.moveTo(-0.025, 1.76);
  c.bezierCurveTo(0.07, 0.7, -0.07, -0.4, -0.01, -1.99);
  c.stroke();
  // The ocellus has an asymmetric teardrop contour, shaded rings and an indigo kidney-shaped pupil.
  const eye = new Path2D(
    'M-.05-1 C-.63-.99-1.02-.27-.78 .38 C-.53 1.02 .28 1.04 .74 .44 C1.12-.05 .61-1.04-.05-1Z',
  );
  c.save();
  c.translate(-0.005, -0.99);
  c.scale(0.59, 0.69);
  const rings = [
    [1, '#cfb566', '#997c39'],
    [0.87, '#e0ca79', '#a68e42'],
    [0.73, '#65c797', '#158372'],
    [0.59, '#32cbc2', '#168caa'],
    [0.46, '#457fde', '#263f94'],
  ] as const;
  for (const [scale, light, dark] of rings) {
    c.save();
    c.scale(scale, scale);
    const shade = c.createRadialGradient(-0.28, -0.32, 0.05, 0.04, 0.02, 1.15);
    shade.addColorStop(0, light);
    shade.addColorStop(0.55, light);
    shade.addColorStop(1, dark);
    c.fillStyle = shade;
    c.globalAlpha = 0.96;
    c.fill(eye);
    c.restore();
  }
  c.fillStyle = '#081a42';
  c.globalAlpha = 0.96;
  c.fill(new Path2D('M.06-.37 C-.26-.48-.51-.18-.36 .1 C-.27 .27 .02 .35 .19 .13 C.35-.04 .28-.25 .06-.37Z'));
  c.strokeStyle = '#8bded5';
  c.lineWidth = 0.012;
  c.globalAlpha = 0.27;
  c.beginPath();
  c.moveTo(-0.27, -0.25);
  c.quadraticCurveTo(-0.43, -0.07, -0.29, 0.09);
  c.stroke();
  c.restore();
  return { image, highlights };
}
