import PropTypes from 'prop-types';
import { Box, Stack, Typography } from '@mui/material';

export default function PageHeader({ title, subtitle, actions }) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ mb: 3, alignItems: { sm: 'flex-end' }, justifyContent: 'space-between' }}
    >
      <Box>
        <Typography variant="h4" component="h1">
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {actions && <Box>{actions}</Box>}
    </Stack>
  );
}

PageHeader.propTypes = {
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.node,
  actions: PropTypes.node,
};
