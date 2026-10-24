export type PhotoLayout = {
  sourceWidth: number;
  sourceHeight: number;
  cropWidth: number;
  cropHeight: number;
  left: number;
  top: number;
  outputWidth: number;
  outputHeight: number;
  cropped: boolean;
  upscaled: boolean;
  upscaleFactor: number;
  severity: 'good' | 'upscaled' | 'low-resolution';
  warning: string;
};
export function photoLayout(width: number, height: number, focusX?: number, focusY?: number): PhotoLayout;
