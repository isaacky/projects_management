import PropTypes from 'prop-types';
import { Alert, AlertTitle } from '@mui/material';
import { DEV_MODE } from '../api/client.js';

function explain(error, title) {
  if (error instanceof TypeError) {
    return ['Cannot reach the server', 'Check that the backend is running on port 8080.'];
  }
  if (error.status === 401) {
    return DEV_MODE
      ? [
          'Not signed in',
          'The web app is in development mode (no sign-in) but the backend wants a sign-in. Either run the ' +
            'backend with only the dev profile, or start the web app with "npm run dev:sso" to sign in ' +
            'through the local Keycloak.',
        ]
      : ['Your session has ended', 'Sign in again to continue.'];
  }
  if (error.status === 403) {
    return ['Not allowed', 'Your account does not have permission for this.'];
  }
  return [title, error.message];
}

export default function ErrorAlert({ error, title = 'Something went wrong' }) {
  if (!error) return null;
  const [heading, detail] = explain(error, title);
  return (
    <Alert severity="error" sx={{ mb: 2 }}>
      <AlertTitle>{heading}</AlertTitle>
      {detail}
    </Alert>
  );
}

ErrorAlert.propTypes = {
  error: PropTypes.instanceOf(Error),
  title: PropTypes.string,
};
