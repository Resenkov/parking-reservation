const tokenKey = 'parkingAuthToken';
const tokenPreview = document.getElementById('token-preview');
const logoutBtn = document.getElementById('logout-btn');
const logEl = document.getElementById('log');

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
  if (tokenPreview) {
    tokenPreview.textContent = token ? token : 'нет';
  }
};

const log = (title, payload) => {
  if (!logEl) {
    return;
  }
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
  const response = await fetch(path, {
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

const setupCommon = () => {
  renderToken();
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      setToken(null);
      log('Выход', { message: 'Токен удалён' });
    });
  }
};

setupCommon();
