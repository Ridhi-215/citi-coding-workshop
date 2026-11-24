// Chart palette, aligned with the app theme and checked with the dataviz palette validator:
// teal for single-series magnitude (the brand primary, lightened for marks), a warm neutral for
// "plan" reference bars, and reserved status colors that always ship with a text label or legend.
export const CHART = {
  series: '#00897b',
  reference: '#c9c3b6',
  good: '#0ca30c',
  warning: '#fab219',
  critical: '#d03b3b',
  neutral: '#8a8984',
  // in-progress deliverables: indigo, because teal next to the gray "not started" slice fails
  // the colour-vision-deficiency separation check
  progress: '#5b5bd6',
};

export const DELIVERABLE_STATUS_CHART_COLORS = {
  completed: CHART.good,
  in_progress: CHART.progress,
  not_started: CHART.neutral,
  blocked: CHART.critical,
};
