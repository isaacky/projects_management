import PropTypes from 'prop-types';
import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material';

/** Full-page message when signing in cannot finish, or the person is not set up in the platform. */
export default function SignInProblem({ title, message, actionLabel, onAction, secondaryLabel, onSecondary }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 }, maxWidth: 520, width: '100%' }}>
        <Stack spacing={2}>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>
            PM Platform
          </Typography>
          <Alert severity="warning">
            <Typography sx={{ fontWeight: 600 }}>{title}</Typography>
            {message}
          </Alert>
          <Stack direction="row" spacing={1}>
            {onAction && (
              <Button variant="contained" onClick={onAction}>
                {actionLabel}
              </Button>
            )}
            {onSecondary && <Button onClick={onSecondary}>{secondaryLabel}</Button>}
          </Stack>
        </Stack>
      </Paper>
    </Box>
  );
}

SignInProblem.propTypes = {
  title: PropTypes.string.isRequired,
  message: PropTypes.node,
  actionLabel: PropTypes.string,
  onAction: PropTypes.func,
  secondaryLabel: PropTypes.string,
  onSecondary: PropTypes.func,
};
