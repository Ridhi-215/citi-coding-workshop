import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import FormDialog from './FormDialog';
import { ApiError } from '../services/apiClient';

const FIELDS = [
  { name: 'name', label: 'Name', required: true },
  { name: 'kind', label: 'Kind', type: 'select', options: [{ value: 'a', label: 'Alpha' }] },
];

const setup = (onSave) => render(
  <FormDialog open title="Thing" fields={FIELDS} initial={{ name: '', kind: 'a' }}
    validate={(f) => (f.name.trim() ? {} : { name: 'Name is required' })} onClose={jest.fn()} onSave={onSave} />,
);

test('client validation blocks submit', async () => {
  const onSave = jest.fn();
  setup(onSave);
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));
  expect(await screen.findByText('Name is required')).toBeInTheDocument();
  expect(onSave).not.toHaveBeenCalled();
});

test('submits trimmed values and maps server field errors', async () => {
  const onSave = jest.fn().mockRejectedValue(
    new ApiError(400, 'VALIDATION_FAILED', 'One or more fields are invalid', { name: 'already exists' }));
  setup(onSave);
  fireEvent.change(screen.getByLabelText(/Name/), { target: { value: '  Widget ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save' }));
  await waitFor(() => expect(onSave).toHaveBeenCalledWith({ name: 'Widget', kind: 'a' }));
  expect(await screen.findByText('already exists')).toBeInTheDocument();
  expect(screen.getByText('One or more fields are invalid')).toBeInTheDocument();
});
