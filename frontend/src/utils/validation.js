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

export const isEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value).trim());

/** Same rules as auth-service: 8-72 characters with at least one letter and one digit. */
export function passwordProblem(password) {
  if (!password) return 'Password is required';
  if (password.length < 8) return 'Must be at least 8 characters';
  if (password.length > 72) return 'Must be at most 72 characters';
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return 'Must contain a letter and a number';
  return null;
}

export function validateRegistration(form) {
  const errors = {};
  if (!form.full_name.trim()) errors.full_name = 'Full name is required';
  if (!form.email.trim()) errors.email = 'Email is required';
  else if (!isEmail(form.email)) errors.email = 'Enter a valid email address';
  const pw = passwordProblem(form.password);
  if (pw) errors.password = pw;
  if (!form.confirm_password) errors.confirm_password = 'Confirm your password';
  else if (form.confirm_password !== form.password) errors.confirm_password = 'Passwords do not match';
  return errors;
}

const isMoney = (v) => /^\d+(\.\d{1,2})?$/.test(String(v).trim());

export function validateDeliverable(form) {
  const errors = {};
  if (!form.project_id) errors.project_id = 'Choose a project';
  if (!form.name.trim()) errors.name = 'Name is required';
  if (!form.due_date) errors.due_date = 'Due date is required';
  return errors;
}

export function validateMember(form) {
  const errors = {};
  if (!form.full_name.trim()) errors.full_name = 'Name is required';
  if (!isEmail(form.email)) errors.email = 'Enter a valid email address';
  return errors;
}

export function validateAllocation(form) {
  const errors = {};
  if (!form.team_member_id) errors.team_member_id = 'Choose a team member';
  if (!form.project_id) errors.project_id = 'Choose a project';
  const pct = Number(form.allocation_percent);
  if (!Number.isInteger(pct) || pct < 1 || pct > 100) errors.allocation_percent = 'Whole number from 1 to 100';
  if (!form.start_date) errors.start_date = 'Start date is required';
  if (form.end_date && form.start_date && form.end_date < form.start_date) {
    errors.end_date = 'Must be on or after the start date';
  }
  return errors;
}

export function validateExpense(form) {
  const errors = {};
  if (!form.category) errors.category = 'Choose a category';
  if (!form.description.trim()) errors.description = 'Description is required';
  if (!isMoney(form.amount) || Number(form.amount) <= 0) errors.amount = 'Positive amount, at most 2 decimals';
  if (!form.incurred_on) errors.incurred_on = 'Date is required';
  return errors;
}
