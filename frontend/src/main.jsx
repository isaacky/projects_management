import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CssBaseline, ThemeProvider } from '@mui/material';
import theme from './theme.js';
import App from './App.jsx';
import { DEV_MODE } from './api/client.js';
import { initSession } from './auth/session.js';
import SignInProblem from './components/SignInProblem.jsx';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // 403 and 404 will not change on a retry.
      retry: (count, error) => count < 1 && ![401, 403, 404].includes(error?.status),
      refetchOnWindowFocus: false,
    },
  },
});

const root = createRoot(document.getElementById('root'));

function render(children) {
  root.render(
    <StrictMode>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </StrictMode>,
  );
}

async function start() {
  if (!DEV_MODE) {
    await initSession(); // signs in first (redirects to Keycloak and back)
  }
  render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>,
  );
}

start().catch((e) => {
  render(
    <SignInProblem
      title="Could not sign you in"
      message={e?.message || 'Something went wrong while signing in.'}
      actionLabel="Try again"
      onAction={() => window.location.assign('/')}
    />,
  );
});
