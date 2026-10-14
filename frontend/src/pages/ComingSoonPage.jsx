import PropTypes from 'prop-types';
import { Link as RouterLink } from 'react-router';
import { Button, Card, CardContent, Typography } from '@mui/material';

export default function ComingSoonPage({ module, notFound = false }) {
  return (
    <Card sx={{ maxWidth: 560 }}>
      <CardContent>
        <Typography variant="h5" component="h1" gutterBottom>
          {module}
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          {notFound
            ? 'There is nothing at this address.'
            : 'This module is on the roadmap and not built yet.'}
        </Typography>
        <Button component={RouterLink} to="/projects" variant="outlined">
          Go to projects
        </Button>
      </CardContent>
    </Card>
  );
}

ComingSoonPage.propTypes = {
  module: PropTypes.string.isRequired,
  notFound: PropTypes.bool,
};
