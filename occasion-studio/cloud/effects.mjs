export const EFFECTS = [
  { id: 'sparkles', label: 'Glowing sparkles', group: 'Celebrations' },
  { id: 'petals', label: 'Petal cascade', group: 'Celebrations' },
  { id: 'confetti', label: 'Confetti shower', group: 'Celebrations' },
  { id: 'flowers', label: 'Floating flowers', group: 'Celebrations' },
  { id: 'diya', label: 'Diya flames & sparks', group: 'Festive & spiritual' },
  { id: 'gulal', label: 'Holi gulal drift', group: 'Festive & spiritual' },
  { id: 'temple', label: 'Temple bells & aura', group: 'Festive & spiritual' },
  { id: 'marigold', label: 'Marigold & rose rain', group: 'Festive & spiritual' },
  { id: 'mantra', label: 'Om mantra glow', group: 'Festive & spiritual' },
  { id: 'starlight', label: 'Starlight shimmer', group: 'Festive & spiritual' },
  { id: 'cosmic', label: 'River ripples & sacred embers', group: 'Festive & spiritual' },
  { id: 'peacock', label: 'Peacock feather shimmer', group: 'Festive & spiritual' },
  { id: 'leaves', label: 'Golden peepal leaf drift', group: 'Festive & spiritual' },
  { id: 'none', label: 'No effects', group: 'Still photograph' },
];
export const normaliseEffect = (effect) => (effect === 'bubbles' ? 'flowers' : effect);
export const validEffect = (effect) => EFFECTS.some((item) => item.id === normaliseEffect(effect));
