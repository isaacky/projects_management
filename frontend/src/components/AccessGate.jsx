import PropTypes from 'prop-types';
import { Box, CircularProgress } from '@mui/material';
import { useMe } from '../api/me.js';
import { DEV_MODE } from '../api/client.js';
import { signOut, signedInClaims } from '../auth/session.js';
import SignInProblem from './SignInProblem.jsx';

/**
 * Shows the app only to people set up in the platform. Someone who signs in to Keycloak but has not
 * been added (Administration > Users and roles), or has been deactivated, gets an explanation
 * instead of a screen full of errors.
 */
export default function AccessGate({ children }) {
  const { data: me, error, isLoading, refetch } = useMe();
  if (DEV_MODE) return children;
  if (isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress aria-label="Loading" />
      </Box>
    );
  }
  if (error?.status === 403) {
    const email = signedInClaims()?.email;
    return (
      <SignInProblem
        title="Your account is not set up yet"
        message={`You signed in${email ? ` as ${email}` : ''}, but you have not been added to PM Platform, or your access has been removed. Ask a system administrator to add you with this email address, then try again.`}
        actionLabel="Try again"
        onAction={() => refetch()}
        secondaryLabel="Sign out"
        onSecondary={signOut}
      />
    );
  }
  if (error || !me) {
    return (
      <SignInProblem
        title="PM Platform is not reachable"
        message={error?.message || 'The server did not answer.'}
        actionLabel="Try again"
        onAction={() => refetch()}
      />
    );
  }
  return children;
}

AccessGate.propTypes = {
  children: PropTypes.node,
};
