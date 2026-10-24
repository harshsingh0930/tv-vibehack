/** Shared browser/server geometry; the server still reads and validates the real image. */
export function photoLayout(width, height, focusX = 0.5, focusY = 0.5) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1)
    throw new Error('Invalid photo dimensions.');
  if (![focusX, focusY].every((value) => Number.isFinite(value) && value >= 0 && value <= 1))
    throw new Error('Invalid focal point.');
  const cropWidth = Math.max(1, Math.min(width, Math.round((height * 16) / 9)));
  const cropHeight = Math.max(1, Math.min(height, Math.round((width * 9) / 16)));
  const scale = Math.max(1280 / cropWidth, 720 / cropHeight);
  const upscaled = scale > 1.0001;
  const severity = !upscaled ? 'good' : scale > 2 ? 'low-resolution' : 'upscaled';
  const warning = !upscaled
    ? ''
    : `The cropped photo is ${cropWidth} × ${cropHeight}, smaller than the TV canvas. ` +
      (severity === 'low-resolution'
        ? 'It will need substantial enlargement and may look soft. A larger original will look better. '
        : 'It will be enlarged to fit and may lose some sharpness. ') +
      'High-quality resizing and gentle sharpening help, but cannot recover missing detail.';
  return {
    sourceWidth: width,
    sourceHeight: height,
    cropWidth,
    cropHeight,
    left: Math.round((width - cropWidth) * focusX),
    top: Math.round((height - cropHeight) * focusY),
    outputWidth: 1280,
    outputHeight: 720,
    cropped: cropWidth !== width || cropHeight !== height,
    upscaled,
    upscaleFactor: Math.round(scale * 1000) / 1000,
    severity,
    warning,
  };
}
