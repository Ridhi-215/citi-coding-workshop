/** Client-side checks mirroring the projects-service rules; the server stays authoritative. */
export function validateProject(form) {
  const errors = {};
  if (!/^[A-Za-z0-9][A-Za-z0-9-]{1,19}$/.test(form.code.trim())) errors.code = 'Use 2-20 letters, digits or "-"';
  if (!form.name.trim()) errors.name = 'Name is required';
  if (!form.start_date) errors.start_date = 'Start date is required';
  if (!form.end_date) errors.end_date = 'End date is required';
  else if (form.start_date && form.end_date < form.start_date) errors.end_date = 'Must be on or after the start date';
  const budget = Number(form.budget_planned);
  if (form.budget_planned === '' || Number.isNaN(budget)) errors.budget_planned = 'Enter an amount';
  else if (budget < 0) errors.budget_planned = 'Cannot be negative';
  else if (!/^\d+(\.\d{1,2})?$/.test(String(form.budget_planned).trim())) errors.budget_planned = 'At most 2 decimals';
  return errors;
}
