document.getElementById('register-form')?.addEventListener('submit', (event) =>
  handleAuth(event, '/api/user/add')
);
