import PropTypes from 'prop-types';
import { Chip } from '@mui/material';
import { PHASE_COLORS, phaseLabel } from '../domain/lifecycle.js';

export default function PhaseChip({ phase, size = 'small' }) {
  return (
    <Chip
      label={phaseLabel(phase)}
      color={PHASE_COLORS[phase] ?? 'default'}
      size={size}
      variant="outlined"
    />
  );
}

PhaseChip.propTypes = {
  phase: PropTypes.string.isRequired,
  size: PropTypes.oneOf(['small', 'medium']),
};
