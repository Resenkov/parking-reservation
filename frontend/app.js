const tokenKey = 'parkingAuthToken';
const tokenPreview = document.getElementById('token-preview');
const logoutBtn = document.getElementById('logout-btn');
const logEl = document.getElementById('log');

const getGatewayUrl = () => document.getElementById('gateway-url').value.replace(/\/$/, '');

const setToken = (token) => {
  if (token) {
    localStorage.setItem(tokenKey, token);
  } else {
    localStorage.removeItem(tokenKey);
  }
  renderToken();
};

const getToken = () => localStorage.getItem(tokenKey);

const renderToken = () => {
  const token = getToken();
  tokenPreview.textContent = token ? token : 'нет';
};

const log = (title, payload) => {
  const time = new Date().toLocaleTimeString();
  const message = `[${time}] ${title}\n${JSON.stringify(payload, null, 2)}\n`;
  logEl.textContent = message + '\n' + logEl.textContent;
};

const apiFetch = async (path, options = {}) => {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`${getGatewayUrl()}${path}`, {
    ...options,
    headers
  });
  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (e) {
    data = text;
  }
  if (!response.ok) {
    log(`Ошибка ${response.status} ${path}`, data);
    throw new Error(data?.message || `Request failed: ${response.status}`);
  }
  log(`OK ${path}`, data ?? {});
  return data;
};

const handleAuth = async (event, path) => {
  event.preventDefault();
  const form = event.target;
  const payload = Object.fromEntries(new FormData(form));
  const data = await apiFetch(path, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  if (data?.token) {
    setToken(data.token);
  }
  form.reset();
};

const setup = () => {
  renderToken();

  document.getElementById('register-form').addEventListener('submit', (event) =>
    handleAuth(event, '/api/user/add')
  );

  document.getElementById('login-form').addEventListener('submit', (event) =>
    handleAuth(event, '/auth/login')
  );

  document.getElementById('account-load').addEventListener('click', async () => {
    await apiFetch('/api/account/me');
  });

  document.getElementById('deposit-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = Object.fromEntries(new FormData(event.target));
    payload.amount = Number(payload.amount);
    await apiFetch('/api/account/deposit', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    event.target.reset();
  });

  document.getElementById('reservation-form').addEventListener('submit', async (event) => {
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

  document.getElementById('reservation-list').addEventListener('click', async () => {
    await apiFetch('/api/reservation/my');
  });

  document.getElementById('reservation-cancel-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = Object.fromEntries(new FormData(event.target));
    await apiFetch(`/api/reservation/cancel/${payload.reservationId}`, {
      method: 'POST'
    });
    event.target.reset();
  });

  logoutBtn.addEventListener('click', () => {
    setToken(null);
    log('Выход', { message: 'Токен удалён' });
  });
};

setup();
