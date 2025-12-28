document.getElementById('account-load')?.addEventListener('click', async () => {
  await apiFetch('/api/account/me');
});

document.getElementById('deposit-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = Object.fromEntries(new FormData(event.target));
  payload.amount = Number(payload.amount);
  await apiFetch('/api/account/deposit', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  event.target.reset();
});

document.getElementById('reservation-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = Object.fromEntries(new FormData(event.target));
  payload.from = new Date(payload.from).toISOString();
  payload.to = new Date(payload.to).toISOString();
  await apiFetch('/api/reservation/book', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  event.target.reset();
});

document.getElementById('reservation-list')?.addEventListener('click', async () => {
  await apiFetch('/api/reservation/my');
});

document.getElementById('reservation-cancel-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = Object.fromEntries(new FormData(event.target));
  await apiFetch(`/api/reservation/cancel/${payload.reservationId}`, {
    method: 'POST'
  });
  event.target.reset();
});
