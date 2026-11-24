// Reference data-viz palette (validated): one hue for single-series magnitude, a neutral for
// "plan" reference bars, and reserved status colors that always ship with a text label.
export const CHART = {
  series: '#2a78d6',
  reference: '#c3c2b7',
  good: '#0ca30c',
  warning: '#fab219',
  critical: '#d03b3b',
  neutral: '#8a8984',
};

export const DELIVERABLE_STATUS_CHART_COLORS = {
  completed: CHART.good,
  in_progress: CHART.series,
  not_started: CHART.neutral,
  blocked: CHART.critical,
};
