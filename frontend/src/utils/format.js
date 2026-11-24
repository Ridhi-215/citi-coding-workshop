const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

export const formatMoney = (value) => (value === null || value === undefined ? '—' : money.format(Number(value)));

export const formatDate = (iso) => {
  if (!iso) return '—';
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

export const labelize = (value) => (value ? value.charAt(0).toUpperCase() + value.slice(1).replace(/_/g, ' ') : '');

export const STATUS_COLORS = {
  planned: 'default',
  active: 'primary',
  on_hold: 'warning',
  completed: 'success',
  cancelled: 'default',
};

export const PRIORITY_COLORS = { low: 'default', medium: 'info', high: 'warning', critical: 'error' };

export const RISK_LABELS = {
  over_budget: 'Over budget',
  budget_warning: 'Budget ≥ 90%',
  past_end_date: 'Past end date',
  overdue_work: 'Overdue deliverables',
  blocked_work: 'Blocked deliverables',
};
